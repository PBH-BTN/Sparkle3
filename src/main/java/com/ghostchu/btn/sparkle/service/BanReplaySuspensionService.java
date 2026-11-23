package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper;
import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper.MailStatus;
import com.ghostchu.btn.sparkle.mapper.BanReplaySuspensionMapper.Notice;
import com.ghostchu.btn.sparkle.service.btn.BtnRuntimeConfigService;
import com.ghostchu.btn.sparkle.service.mail.MailDeliveryException;
import com.ghostchu.btn.sparkle.service.mail.MailSettingsService;
import com.ghostchu.btn.sparkle.service.mail.PlainTextMailTemplate;
import com.ghostchu.btn.sparkle.service.mail.SmtpMailService;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BanReplaySuspensionService {
    private static final String SECTION = "banReplaySuspension";
    private static final long SCAN_INTERVAL = 300_000;
    private static final long MAIL_INTERVAL = 60_000;
    private static final int SCAN_LIMIT = 200;
    private static final int MAIL_LIMIT = 10;
    private static final int MAX_MAIL_ATTEMPTS = 5;
    private static final String REASON = "封禁日志重传超限 / Ban log replay limit exceeded";

    private final BanReplaySuspensionMapper mapper;
    private final BtnRuntimeConfigService config;
    private final SmtpMailService smtp;

    @Scheduled(fixedDelay = SCAN_INTERVAL, initialDelay = SCAN_INTERVAL)
    @MonitoredScheduledTask(id = "btn-ban-replay-suspension", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.btn-ban-replay-suspension.name",
            descriptionKey = "admin.tasks.items.btn-ban-replay-suspension.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = SCAN_INTERVAL, initialDelayMillis = SCAN_INTERVAL)
    public void suspendApplications() {
        boolean checkBatches = config.booleanValue(SECTION, "batchDetectionEnabled");
        boolean checkLogs = config.booleanValue(SECTION, "logDetectionEnabled");
        if (!checkBatches && !checkLogs) {
            MonitoredTaskOutcome.skipped("Replay suspension checks are disabled");
            return;
        }
        var since = LocalDate.now(ZoneOffset.UTC).minusDays(BanSubmissionGuard.REPLAY_WINDOW_DAYS - 1);
        int suspended = mapper.suspendAndQueue(since, checkBatches, config.longValue(SECTION, "batchLimit"),
                checkLogs, config.longValue(SECTION, "logLimit"), REASON, SCAN_LIMIT);
        if (suspended == 0) {
            MonitoredTaskOutcome.skipped("No applications crossed a replay limit");
        } else {
            log.info("Automatically suspended {} BTN applications for exceeding ban replay limits", suspended);
        }
    }

    @Scheduled(fixedDelay = MAIL_INTERVAL, initialDelay = MAIL_INTERVAL)
    @MonitoredScheduledTask(id = "btn-ban-replay-suspension-mail", group = TaskGroup.MAINTENANCE,
            nameKey = "admin.tasks.items.btn-ban-replay-suspension-mail.name",
            descriptionKey = "admin.tasks.items.btn-ban-replay-suspension-mail.description",
            scheduleType = TaskScheduleType.FIXED_DELAY, intervalMillis = MAIL_INTERVAL, initialDelayMillis = MAIL_INTERVAL)
    public void deliverNotices() {
        if (!smtp.available()) {
            MonitoredTaskOutcome.skipped("SMTP is unavailable");
            return;
        }
        String subject = config.text(SECTION, "emailSubject");
        String body = config.text(SECTION, "emailBody");
        for (int index = 0; index < MAIL_LIMIT; index++) {
            Notice notice = mapper.claimNotice();
            if (notice == null) {
                if (index == 0) {
                    MonitoredTaskOutcome.skipped("No pending application suspension notifications");
                }
                return;
            }
            deliverNotice(notice, subject, body);
        }
    }

    private void deliverNotice(Notice notice, String subject, String body) {
        String recipient = notice.email();
        if (!MailSettingsService.validAddress(recipient) || recipient.toLowerCase(Locale.ROOT).endsWith("@sparkle.system")) {
            mapper.finishNotice(notice.id(), notice.attempt(), MailStatus.SKIPPED);
            return;
        }
        var variables = Map.of(
                "recipientName", notice.nickname() == null || notice.nickname().isBlank() ? "BTN" : notice.nickname(),
                "appId", notice.appId(),
                "applicationId", Long.toString(notice.applicationId()),
                "replayBatches", Long.toString(notice.replayBatches()),
                "replayLogs", Long.toString(notice.replayLogs()),
                "batchLimit", Long.toString(notice.batchLimit()),
                "logLimit", Long.toString(notice.logLimit()),
                "windowDays", Integer.toString(BanSubmissionGuard.REPLAY_WINDOW_DAYS),
                "suspendedAt", notice.suspendedAt().withOffsetSameInstant(ZoneOffset.UTC).toString()
        );
        try {
            smtp.send(null, recipient, PlainTextMailTemplate.render(subject, variables),
                    PlainTextMailTemplate.render(body, variables), null);
        } catch (MailDeliveryException exception) {
            MonitoredTaskOutcome.failure("Unable to deliver a suspension email");
            log.warn("Suspension email for BTN application {} failed on attempt {}",
                    notice.applicationId(), notice.attempt());
            if (notice.attempt() >= MAX_MAIL_ATTEMPTS) {
                mapper.finishNotice(notice.id(), notice.attempt(), MailStatus.FAILED);
            }
            return;
        }
        mapper.finishNotice(notice.id(), notice.attempt(), MailStatus.SENT);
    }
}
