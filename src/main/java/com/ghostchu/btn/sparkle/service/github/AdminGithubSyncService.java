package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.RunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.SettingsView;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.SourceView;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetRunResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetView;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.UpdateSettingsRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.btn.BtnDynamicScheduler;
import com.ghostchu.btn.sparkle.service.impl.GithubSyncServiceImpl;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AdminGithubSyncService {
    private final GithubRuleSourceCatalog sourceCatalog;
    private final GithubSyncTargetService targetService;
    private final GithubSyncSettingsService settingsService;
    private final GithubSyncServiceImpl githubSyncService;
    private final BtnDynamicScheduler dynamicScheduler;

    @Transactional(rollbackFor = Exception.class)
    public SettingsView get() throws BusinessException {
        targetService.seedLegacyTargetIfNeeded(settingsService);
        return view();
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView create(TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        targetService.seedLegacyTargetIfNeeded(settingsService);
        return targetService.create(input, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView update(long id, TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        return targetService.update(id, input, operatorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) throws BusinessException {
        targetService.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public SettingsView updateSettings(
            UpdateSettingsRequest request,
            @Nullable Long operatorId
    ) throws BusinessException {
        targetService.seedLegacyTargetIfNeeded(settingsService);
        settingsService.update(
                request.schedule(),
                request.personalAccessToken(),
                Boolean.TRUE.equals(request.clearToken()),
                operatorId
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dynamicScheduler.refreshSchedules();
                        }
                    }
            );
        } else {
            dynamicScheduler.refreshSchedules();
        }
        return view();
    }

    public RunResult runAll() {
        return githubSyncService.scheduleSync();
    }

    public TargetRunResult runTarget(long id) throws BusinessException {
        return githubSyncService.syncTarget(id);
    }

    private SettingsView view() {
        return new SettingsView(
                settingsService.schedule(),
                settingsService.tokenConfigured(),
                sourceCatalog.definitions().stream()
                        .map(definition -> new SourceView(
                                definition.id(),
                                definition.kind(),
                                definition.defaultFileName(),
                                sourceCatalog.isAvailable(definition.id())
                        ))
                        .toList(),
                targetService.list()
        );
    }
}
