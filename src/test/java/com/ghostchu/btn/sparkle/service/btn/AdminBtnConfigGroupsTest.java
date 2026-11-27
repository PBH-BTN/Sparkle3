package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBtnConfigGroupsTest {
    @Test
    void everyLegacyFieldBelongsToOneNonemptyEditor() {
        var assigned = BtnConfigCatalog.definitions().stream().collect(Collectors.groupingBy(AdminBtnConfigGroups::groupOf));
        assertEquals(new java.util.HashSet<>(AdminBtnConfigGroups.GROUPS), assigned.keySet());
        assertEquals(BtnConfigCatalog.definitions().size(), assigned.values().stream().mapToInt(java.util.List::size).sum());
    }

    @Test
    void scopedEditorCannotWriteAnotherFormsFields() {
        var database = mock(DatabaseConfigService.class);
        var service = new AdminBtnConfigService(database,
                new BtnRuntimeConfigService(database, new MockEnvironment()), mock(BtnDynamicScheduler.class));
        assertThrows(BusinessException.class, () -> service.updateGroup("ranking",
                Map.of("sparkle.ping.config.min-protocol-version", "99"), 1L));
        assertThrows(BusinessException.class, () -> service.updateGroup("unknown", Map.of(), 1L));
        verifyNoInteractions(database);
    }
}
