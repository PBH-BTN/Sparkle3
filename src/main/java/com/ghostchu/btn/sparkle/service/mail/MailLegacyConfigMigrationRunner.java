package com.ghostchu.btn.sparkle.service.mail;

import com.ghostchu.btn.sparkle.service.tasks.MonitoredScheduledTask;
import com.ghostchu.btn.sparkle.service.tasks.MonitoredTaskOutcome;
import com.ghostchu.btn.sparkle.service.tasks.TaskGroup;
import com.ghostchu.btn.sparkle.service.tasks.TaskScheduleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class MailLegacyConfigMigrationRunner implements ApplicationRunner {
    private final MailSettingsService settingsService;

    @Override
    @MonitoredScheduledTask(id = "mail-config-migration", group = TaskGroup.STARTUP,
            nameKey = "admin.tasks.items.mail-config-migration.name",
            descriptionKey = "admin.tasks.items.mail-config-migration.description",
            scheduleType = TaskScheduleType.STARTUP)
    public void run(ApplicationArguments args) {
        try {
            settingsService.migrateLegacySpringMail();
        } catch (Exception exception) {
            MonitoredTaskOutcome.failure("One or more operations failed; see server logs");
            log.warn("Unable to migrate legacy spring.mail settings: {}", exception.getMessage());
        }
    }
}
