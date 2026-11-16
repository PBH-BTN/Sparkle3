package com.ghostchu.btn.sparkle.service.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryQueryDto;
import com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto.ClientDiscoveryQueryDto;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.Category;
import com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto.SwarmTrackerQueryDto;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.ExportQueryMapper;
import com.ghostchu.btn.sparkle.mapper.SubmissionMapper;
import com.ghostchu.btn.sparkle.mapper.UserTrafficStatisticsMapper;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.service.impl.BanHistoryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.ClientDiscoveryServiceImpl;
import com.ghostchu.btn.sparkle.service.impl.SwarmTrackerServiceImpl;
import com.ghostchu.btn.sparkle.util.InfoHashUtil;
import com.google.common.net.InetAddresses;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ExportQueries {
    private static final List<String> BANS = List.of("populate_time", "torrent_id", "peer_ip", "peer_port",
            "peer_id", "peer_client_name", "peer_progress", "from_peer_traffic", "to_peer_traffic",
            "geo", "module_name", "rule", "description", "structured_data");
    private static final List<String> CLIENTS = List.of("peer_id", "peer_client_name", "client_type", "client_semver", "found_at");
    private static final List<String> SWARM = List.of("torrent_id", "peer_ip", "peer_port", "peer_id",
            "peer_client_name", "peer_progress", "from_peer_traffic", "to_peer_traffic", "flags",
            "first_time_seen", "last_time_seen", "user_progress");
    private static final List<String> TRAFFIC = List.of("day", "upload", "download", "upload_ack", "download_ack",
            "seeding", "downloading", "loss", "loss_ratio");
    private static final List<String> SUBMITTED_BANS = List.of("application", "populate_time", "torrent_id",
            "peer_ip", "peer_port", "peer_id", "client_name", "peer_progress", "geo", "module_name",
            "rule", "description", "reporter_progress", "flags", "uploaded", "downloaded", "structured_data");
    private static final List<String> SUBMITTED_CLIENTS = List.of("application", "submitted_at", "peer_id",
            "client_name", "client_type", "client_version");
    private static final List<String> SUBMITTED_SWARM = List.of("application", "torrent_id", "peer_ip", "peer_port",
            "peer_id", "client_name", "peer_progress", "downloaded", "uploaded", "flags", "first_time_seen",
            "last_time_seen", "user_progress");

    private final ExportQueryMapper mapper;
    private final SubmissionMapper submissions;
    private final UserTrafficStatisticsMapper traffic;
    private final ITorrentService torrents;
    private final ObjectMapper json;

    public String validate(Category category, Map<String, Object> filters, OffsetDateTime now) throws BusinessException {
        try {
            String serialized = json.writeValueAsString(filters == null ? Map.of() : filters);
            if (serialized.length() > 8_192) throw new IllegalArgumentException();
            return switch (category) {
                case BANS -> {
                    var query = json.readValue(serialized, BanHistoryQueryDto.class);
                    checkIp(query.getPeerIp());
                    BanHistoryServiceImpl.buildQuery(query, now);
                    yield json.writeValueAsString(query);
                }
                case SWARM -> {
                    var query = json.readValue(serialized, SwarmTrackerQueryDto.class);
                    checkIp(query.getPeerIp());
                    yield json.writeValueAsString(query);
                }
                case CLIENTS -> json.writeValueAsString(json.readValue(serialized, ClientDiscoveryQueryDto.class));
                case SUBMISSIONS -> {
                    var query = json.readValue(serialized, SubmissionFilter.class);
                    if (query.type() == null || !Set.of("bans", "clients", "swarm").contains(query.type())
                            || !Set.of(1, 3, 7).contains(query.days())) throw new IllegalArgumentException();
                    yield json.writeValueAsString(query);
                }
                case TRAFFIC -> "{}";
            };
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "export_invalid_filters");
        }
    }

    // PostgreSQL fetches the cursor in bounded batches when autocommit is disabled.
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public XzCsvWriter.Result write(DataExportJob job, Path part, XzCsvWriter.Heartbeat heartbeat) throws IOException {
        long limit = (long) job.maxRows() + 1;
        List<String> columns = columns(job);
        try (Cursor<Map<String, Object>> cursor = open(job, limit)) {
            var records = cursor == null ? List.<Map<String, Object>>of() : cursor;
            return XzCsvWriter.write(part, columns, records, job.maxRows(), job.maxBytes(), heartbeat, this::geo);
        }
    }

    private List<String> columns(DataExportJob job) throws JsonProcessingException {
        return switch (job.category()) {
            case BANS -> BANS;
            case CLIENTS -> CLIENTS;
            case SWARM -> SWARM;
            case TRAFFIC -> TRAFFIC;
            case SUBMISSIONS -> switch (json.readValue(job.filters(), SubmissionFilter.class).type()) {
                case "bans" -> SUBMITTED_BANS;
                case "clients" -> SUBMITTED_CLIENTS;
                default -> SUBMITTED_SWARM;
            };
        };
    }

    private Cursor<Map<String, Object>> open(DataExportJob job, long limit) throws JsonProcessingException {
        return switch (job.category()) {
            case BANS -> {
                var q = json.readValue(job.filters(), BanHistoryQueryDto.class);
                yield mapper.bans(BanHistoryServiceImpl.buildQuery(q, job.requestedAt())
                        .orderByDesc("b.populate_time", "b.id"), limit);
            }
            case CLIENTS -> {
                var q = json.readValue(job.filters(), ClientDiscoveryQueryDto.class);
                yield mapper.clients(ClientDiscoveryServiceImpl.buildQuery(q.getPeerId(), q.getPeerClientName(),
                        q.getClientType(), q.getClientSemver(), q.getSortBy(), q.getSortOrder())
                        .orderByDesc("hash"), limit);
            }
            case SWARM -> {
                var q = json.readValue(job.filters(), SwarmTrackerQueryDto.class);
                Long torrentId = null;
                if (q.getInfoHash() != null && !q.getInfoHash().isBlank()) {
                    var torrent = torrents.getTorrentByTorrentIdentifier(InfoHashUtil.getHashedIdentifier(q.getInfoHash()));
                    if (torrent == null) yield null;
                    torrentId = torrent.getId();
                }
                yield mapper.swarm(SwarmTrackerServiceImpl.buildQuery(torrentId, q.getPeerIp(), q.getPeerPort(),
                        q.getPeerId(), q.getPeerClientName(), q.getPeerProgress(), q.getFromPeerTraffic(),
                        q.getToPeerTraffic(), q.getFlags(), parseTime(q.getFirstTimeSeenAfter()),
                        parseTime(q.getLastTimeSeenAfter()), q.getUserProgress(), q.getSortBy(), q.getSortOrder())
                        .orderByDesc("s.id"), limit);
            }
            case TRAFFIC -> traffic.exportDaily(job.userId(), limit);
            case SUBMISSIONS -> {
                var filter = json.readValue(job.filters(), SubmissionFilter.class);
                var ids = submissions.findApplicationIds(job.userId());
                yield ids.isEmpty() ? null : submissions.exportRows(job.userId(), ids, filter.type(),
                        job.requestedAt().minusDays(filter.days()), job.requestedAt(), limit);
            }
        };
    }

    private String geo(Object raw) throws JsonProcessingException {
        var data = json.readTree(raw.toString());
        var country = data.path("country");
        var city = data.path("city");
        return String.join(", ", Stream.of(
                        country.path("iso").asText(country.path("name").asText("")),
                        city.path("cnProvince").asText(""),
                        city.path("cnCity").asText(city.path("name").asText("")),
                        data.path("network").path("isp").asText("").replaceFirst("^中国(?=电信|联通|移动|广电)", ""))
                .filter(value -> !value.isBlank()).distinct().toList());
    }

    private static void checkIp(String ip) {
        if (ip == null || ip.isBlank()) return;
        String[] bits = ip.split("/", -1);
        var address = InetAddresses.forString(bits[0]);
        if (bits.length > 2 || (bits.length == 2 && (!bits[1].matches("[0-9]{1,3}")
                || Integer.parseInt(bits[1]) > address.getAddress().length * 8))) {
            throw new IllegalArgumentException();
        }
    }

    private static OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private record SubmissionFilter(String type, int days) {
    }
}
