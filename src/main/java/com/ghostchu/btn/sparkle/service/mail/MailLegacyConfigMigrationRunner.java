package com.ghostchu.btn.sparkle.service.mail;

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
    public void run(ApplicationArguments args) {
        try {
            settingsService.migrateLegacySpringMail();
        } catch (Exception exception) {
            log.warn("Unable to migrate legacy spring.mail settings: {}", exception.getMessage());
        }
    }
}
