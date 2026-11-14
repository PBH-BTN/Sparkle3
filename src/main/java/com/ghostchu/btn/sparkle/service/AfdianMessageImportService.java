package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.ImportRequest;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.ImportStatus;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.tasks.TaskDefinition;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfdianMessageImportService {
    private static final String TASK_ID = "afdian-message-license-import";
    private final AfdianMessageClient client;
    private final PbhLicenseDecoder decoder;
    private final AfdianMessageLicenseWriter writer;
    private final AfdianSettingsService settings;
    private final TaskStatusService tasks;
    private final AfdianEmailBindingService emailBinding;
    private volatile ImportStatus status = new ImportStatus("IDLE", 0, 0, 0, 0, 0, 0, 0);

    public ImportStatus status() { return status; }

    public synchronized ImportStatus start(ImportRequest request, Long operatorId) throws BusinessException {
        if ("RUNNING".equals(status.state())) throw new BusinessException(HttpStatus.CONFLICT, "A scan is already running.");
        if (request == null || request.authToken() == null
                || !request.authToken().matches("[A-Za-z0-9_-]{1,512}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Enter a valid Afdian session credential.");
        }
        List<PublicKey> keys;
        try {
            String privateKey = request.verificationPrivateKey();
            if (privateKey != null && privateKey.length() > 16384) throw new IllegalArgumentException();
            keys = decoder.verificationKeys(privateKey == null || privateKey.isBlank() ? settings.licensePrivateKey() : privateKey);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Unable to read the license verification key.");
        }
        tasks.ensure(TaskDefinition.event(TASK_ID, TaskGroup.SYNC,
                "admin.tasks.items.afdian-message-license-import.name",
                "admin.tasks.items.afdian-message-license-import.description"));
        status = new ImportStatus("RUNNING", 0, 0, 0, 0, 0, 0, 0);
        // Capture only the token and derived public keys, never the private key/request.
        String token = request.authToken();
        Thread.startVirtualThread(() -> run(token, keys, operatorId));
        return status;
    }

    void run(String token, List<PublicKey> keys, Long operatorId) {
        var execution = tasks.begin(TASK_ID, "ADMIN");
        Counts counts = new Counts();
        try {
            Set<String> dialogs = new HashSet<>();
            for (int page = 1; ; page++) {
                JsonNode data = client.dialogs(token, page);
                int added = 0;
                for (JsonNode dialog : data.path("list")) {
                    String userId = dialog.path("user").path("user_id").asText();
                    if (userId.isBlank()) throw new IllegalStateException("Invalid dialog");
                    if (!dialogs.add(userId)) continue;
                    added++;
                    scanDialog(token, userId, keys, operatorId, counts);
                    counts.dialogs++;
                    publish("RUNNING", counts);
                }
                boolean more = data.path("has_more").asInt() != 0 || data.path("has_more").asBoolean()
                        || page < data.path("total_page").asInt(page);
                if (!more) break;
                if (added == 0) throw new IllegalStateException("Dialog pagination did not advance");
            }
            publish("COMPLETED", counts);
            execution.success();
            log.info("Afdian message scan completed by administrator {}: {} dialogs, {} messages, {} imported, {} duplicates, {} unresolved",
                    operatorId, counts.dialogs, counts.messages, counts.imported, counts.duplicates, counts.unmatched);
        } catch (Exception exception) {
            publish("FAILED", counts);
            execution.failure("Message scan failed; already imported licenses were retained. Retry the scan.");
            // Do not log exception messages, request credentials, or chat contents.
            log.warn("Afdian message scan failed for administrator {} ({})", operatorId, exception.getClass().getSimpleName());
        }
    }

    private void scanDialog(String token, String userId, List<PublicKey> keys, Long operatorId, Counts counts) throws Exception {
        String cursor = "0";
        Set<String> seen = new HashSet<>();
        List<Occurrence> awaitingCard = new ArrayList<>();
        Map<String, Occurrence> licenses = new LinkedHashMap<>();
        while (true) {
            JsonNode data = client.messages(token, userId, cursor);
            List<JsonNode> entries = new ArrayList<>();
            for (JsonNode entry : data.path("list")) {
                if (!entry.path("message").path("msg_id").asText().matches("[0-9]{1,30}")) {
                    throw new IllegalStateException("Invalid message cursor");
                }
                entries.add(entry);
            }
            entries.sort(Comparator.comparing((JsonNode entry) ->
                    new BigInteger(entry.path("message").path("msg_id").asText())).reversed());
            BigInteger oldest = null;
            int added = 0;
            for (JsonNode entry : entries) {
                JsonNode message = entry.path("message");
                String id = message.path("msg_id").asText();
                if (!id.matches("[0-9]{1,30}")) throw new IllegalStateException("Invalid message cursor");
                BigInteger numericId = new BigInteger(id);
                if (oldest == null || numericId.compareTo(oldest) < 0) oldest = numericId;
                if (!seen.add(id)) continue;
                added++;
                counts.messages++;
                if (AfdianMessageReceipt.isOrderCard(entry)) {
                    AfdianMessageReceipt receipt = AfdianMessageReceipt.from(entry);
                    if (receipt != null) {
                        try {
                            emailBinding.bind(userId, receipt.orderId(), receipt.remark());
                        } catch (BusinessException | org.springframework.dao.DataAccessException exception) {
                            log.warn("Afdian remark binding skipped during message scan ({})", exception.getClass().getSimpleName());
                        }
                    }
                    associate(awaitingCard, licenses, receipt, counts);
                }
                scanContent(message.path("content"), id, keys, awaitingCard);
            }
            publish("RUNNING", counts);
            boolean more = data.path("has_more").asInt() != 0 || data.path("has_more").asBoolean();
            if (!more) break;
            if (added == 0 || oldest == null || oldest.toString().equals(cursor)) throw new IllegalStateException("Message pagination did not advance");
            cursor = oldest.toString();
        }
        associate(awaitingCard, licenses, null, counts);
        for (Occurrence occurrence : licenses.values()) {
            String result = occurrence.receipt() == null
                    ? writer.register(userId, occurrence.messageId(), occurrence.license(), operatorId)
                    : writer.register(userId, occurrence.messageId(), occurrence.license(), operatorId, occurrence.receipt());
            switch (result) {
                case "IMPORTED" -> counts.imported++;
                case "DUPLICATE" -> counts.duplicates++;
                case "UNMATCHED" -> counts.unmatched++;
                case "RECONCILED" -> counts.reconciled++;
                case "CONFLICT" -> counts.conflicts++;
            }
        }
    }

    private void scanContent(JsonNode content, String id, List<PublicKey> keys, List<Occurrence> pending) {
        if (content.isTextual()) {
            for (var license : decoder.scan(content.asText(), keys)) pending.add(new Occurrence(id, license, null));
        } else if (content.isContainerNode()) {
            for (JsonNode child : content) scanContent(child, id, keys, pending);
        }
    }

    private void associate(List<Occurrence> pending, Map<String, Occurrence> licenses,
                           AfdianMessageReceipt receipt, Counts counts) {
        for (Occurrence occurrence : pending) {
            // Scanning backwards lets the oldest occurrence win when a key was later resent.
            if (licenses.put(occurrence.license().key(), new Occurrence(occurrence.messageId(), occurrence.license(), receipt)) != null) {
                counts.duplicates++;
            }
        }
        pending.clear();
    }

    private record Occurrence(String messageId, PbhLicenseDecoder.Decoded license, AfdianMessageReceipt receipt) {
        @Override
        public String toString() { return "Occurrence[redacted]"; }
    }

    private void publish(String state, Counts c) {
        status = new ImportStatus(state, c.dialogs, c.messages, c.imported, c.duplicates, c.unmatched, c.reconciled, c.conflicts);
    }

    private static class Counts {
        int dialogs, messages, imported, duplicates, unmatched, reconciled, conflicts;
    }
}
