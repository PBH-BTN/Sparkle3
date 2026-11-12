package com.ghostchu.btn.sparkle.service.btn;

import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.BtnConfigFieldView;
import com.ghostchu.btn.sparkle.controller.admin.dto.BtnConfigModels.BtnConfigView;
import com.ghostchu.btn.sparkle.controller.admin.dto.DatabaseConfigModels.ConfigEntry;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminBtnConfigService {
    private final DatabaseConfigService databaseConfigService;
    private final BtnRuntimeConfigService runtimeConfigService;
    private final BtnDynamicScheduler dynamicScheduler;

    @Transactional
    public BtnConfigView get() {
        ensureEntries();
        Map<String, ConfigEntry> entries = loadEntries();
        Map<String, String> rawValues = databaseConfigService.getRawValues(
                BtnConfigCatalog.keys()
        );
        OffsetDateTime updatedAt = entries.values().stream()
                .map(ConfigEntry::updatedAt)
                .filter(value -> value != null)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        List<BtnConfigFieldView> fields = BtnConfigCatalog.definitions().stream()
                .map(definition -> toView(
                        definition,
                        entries.get(definition.key()),
                        rawValues.get(definition.key())
                ))
                .toList();
        return new BtnConfigView(fields, updatedAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public BtnConfigView update(
            Map<String, String> requestedValues,
            @Nullable Long operatorId
    ) throws BusinessException {
        ensureEntries();
        Map<String, ConfigEntry> entries = loadEntries();
        Map<String, String> rawValues = databaseConfigService.getRawValues(
                BtnConfigCatalog.keys()
        );
        Map<String, String> currentValues = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigEntry> entry : entries.entrySet()) {
            currentValues.put(
                    entry.getKey(),
                    rawValues.get(entry.getKey())
            );
        }
        Map<String, String> normalized = BtnConfigValidator.validate(
                requestedValues,
                currentValues
        );
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            BtnConfigCatalog.Definition definition = BtnConfigCatalog.definition(entry.getKey());
            if (definition != null && definition.sensitive()) {
                databaseConfigService.updateSensitive(
                        entry.getKey(),
                        entry.getValue(),
                        operatorId
                );
            } else {
                databaseConfigService.update(entry.getKey(), entry.getValue(), operatorId);
            }
        }
        refreshRuntimeAfterCommit();
        return get();
    }

    private void refreshRuntimeAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            refreshRuntime();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        refreshRuntime();
                    }
                }
        );
    }

    private void refreshRuntime() {
        runtimeConfigService.invalidate();
        dynamicScheduler.refreshSchedules();
    }

    private void ensureEntries() {
        Map<String, String> existing = databaseConfigService.getRawValues(BtnConfigCatalog.keys());
        for (BtnConfigCatalog.Definition definition : BtnConfigCatalog.definitions()) {
            if (existing.containsKey(definition.key())) {
                continue;
            }
            String currentDefault = runtimeConfigService.defaultValue(definition.key());
            databaseConfigService.insertIfMissing(
                    definition.key(),
                    currentDefault == null ? definition.defaultValue() : currentDefault,
                    "yaml"
            );
        }
    }

    private Map<String, ConfigEntry> loadEntries() {
        Map<String, ConfigEntry> entries = new LinkedHashMap<>();
        for (ConfigEntry entry : databaseConfigService.list("", "")) {
            if (BtnConfigCatalog.definition(entry.key()) != null) {
                entries.put(entry.key(), entry);
            }
        }
        return entries;
    }

    private BtnConfigFieldView toView(
            BtnConfigCatalog.Definition definition,
            ConfigEntry entry,
            String rawValue
    ) {
        if (entry == null) {
            return new BtnConfigFieldView(
                    definition.key(),
                    definition.section(),
                    definition.field(),
                    definition.type().name().toLowerCase(),
                    definition.sensitive() ? null
                            : runtimeConfigService.text(definition.section(), definition.field()),
                    definition.sensitive() ? ""
                            : runtimeConfigService.defaultValue(definition.key()),
                    definition.min(),
                    definition.max(),
                    definition.sensitive(),
                    definition.sensitive() && rawValue != null && !rawValue.isBlank(),
                    true,
                    false,
                    "yaml",
                    null
            );
        }
        return new BtnConfigFieldView(
                definition.key(),
                definition.section(),
                definition.field(),
                definition.type().name().toLowerCase(),
                definition.sensitive() ? null : entry.value(),
                definition.sensitive() ? "" : runtimeConfigService.defaultValue(definition.key()),
                definition.min(),
                definition.max(),
                definition.sensitive(),
                definition.sensitive() && rawValue != null && !rawValue.isBlank(),
                entry.hotReload(),
                entry.environmentOverride(),
                entry.source(),
                entry.updatedAt()
        );
    }
}
