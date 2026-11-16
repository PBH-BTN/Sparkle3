package com.ghostchu.btn.sparkle.autoconfig;

import io.sentry.SentryBaseEvent;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExportTelemetryConfig {
    @Bean
    SentryOptions.BeforeSendCallback redactExportDownloadFromErrors() {
        return (event, hint) -> {
            redact(event);
            return event;
        };
    }

    @Bean
    SentryOptions.BeforeSendTransactionCallback redactExportDownloadFromTransactions() {
        return (event, hint) -> {
            redact(event);
            return event;
        };
    }

    private static void redact(SentryBaseEvent event) {
        var request = event.getRequest();
        if (request == null || request.getUrl() == null) return;
        String path = request.getUrl().split("\\?", 2)[0];
        if (path.contains("/api/ui/exports/") && path.endsWith("/download")) {
            request.setUrl(path);
            request.setQueryString(null);
        }
    }
}
