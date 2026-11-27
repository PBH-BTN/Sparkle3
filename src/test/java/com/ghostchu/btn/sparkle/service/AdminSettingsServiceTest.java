package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AdminSettingsModels.*;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminSettingsServiceTest {
    private final DatabaseConfigService database = mock(DatabaseConfigService.class);
    private final AdminConfigService runtime = mock(AdminConfigService.class);
    private final AdminSettingsService service = new AdminSettingsService(runtime, database);

    @Test
    void savingStatisticsDoesNotResubmitHiddenProtocolMailOrDisplayFields() throws Exception {
        service.updateStatistics(new StatisticsSettings(true, 30, 14), 12L);
        verify(database).update("sparkle.swarm-statistics-track.enabled", "true", 12L);
        verify(database).update("sparkle.swarm-statistics-track.refresh-interval-minutes", "30", 12L);
        verify(database).update("sparkle.swarm-statistics-track.window-days", "14", 12L);
        verify(database, times(3)).insertIfMissing(anyString(), anyString(), eq("database"));
        verifyNoMoreInteractions(database);
        verify(runtime).invalidateAfterCommit();
    }

    @Test
    void rejectsInvalidStatisticsBeforeWritingAnything() {
        for (var settings : new StatisticsSettings[]{new StatisticsSettings(true, 0, 7),
                new StatisticsSettings(true, 10_081, 7), new StatisticsSettings(true, 60, 0),
                new StatisticsSettings(true, 60, 91)}) {
            assertThrows(BusinessException.class, () -> service.updateStatistics(settings, 12L));
        }
        verifyNoInteractions(database, runtime);
    }

    @Test
    void templatesAndFeaturesHaveIndependentWrites() throws Exception {
        service.updateContactTemplate(new ContactTemplateSettings("{{title}}", "{{content}}"), 12L);
        verify(database).update("sparkle.user-contact-email.subject-template", "{{title}}", 12L);
        verify(database).update("sparkle.user-contact-email.content-template", "{{content}}", 12L);
        verify(database, times(2)).insertIfMissing(anyString(), anyString(), eq("database"));
        verifyNoMoreInteractions(database);
        clearInvocations(database);
        service.updateFeatures(new SiteFeatureSettings(false), 12L);
        verify(database).insertIfMissing("sparkle.client-discovery.enabled", "false", "database");
        verify(database).update("sparkle.client-discovery.enabled", "false", 12L);
        verifyNoMoreInteractions(database);
    }

    @Test
    void rejectsInvalidTemplatesBeforeWritingAnything() {
        assertThrows(BusinessException.class, () -> service.updateContactTemplate(new ContactTemplateSettings("title", "{{content}}"), 12L));
        assertThrows(BusinessException.class, () -> service.updateContactTemplate(new ContactTemplateSettings("{{title}}", "body"), 12L));
        verifyNoInteractions(database, runtime);
    }

    @Test
    void displaySaveOnlyChangesSixDisplaySettings() throws Exception {
        service.updateHomepageDisplay(new HomepageDisplaySettings(true, false, true, false, true, true), 12L);
        verify(database, times(6)).update(startsWith("sparkle.homepage."), anyString(), eq(12L));
        verify(database, times(6)).insertIfMissing(startsWith("sparkle.homepage."), anyString(), eq("database"));
        verifyNoMoreInteractions(database);
    }
}
