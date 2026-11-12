package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetView;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ghostchu.btn.sparkle.entity.GithubSyncTarget;
import com.ghostchu.btn.sparkle.entity.GithubSyncTargetSource;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.GithubSyncTargetMapper;
import com.ghostchu.btn.sparkle.mapper.GithubSyncTargetSourceMapper;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GithubSyncTargetService {
    private static final Pattern REPOSITORY_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern BRANCH_NAME = Pattern.compile("[A-Za-z0-9._/-]+");

    private final GithubSyncTargetMapper targetMapper;
    private final GithubSyncTargetSourceMapper sourceMapper;
    private final GithubRuleSourceCatalog sourceCatalog;
    private final DatabaseConfigService databaseConfigService;

    public List<TargetView> list() {
        List<TargetRow> rows = targetMapper.selectList(
                new QueryWrapper<GithubSyncTarget>()
                        .orderByAsc("name")
                        .orderByAsc("id")
        ).stream().map(TargetRow::from).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> sources = loadSources();
        return rows.stream()
                .map(row -> row.toView(sources.getOrDefault(row.id(), List.of())))
                .toList();
    }

    public @Nullable TargetView find(long id) {
        GithubSyncTarget entity = targetMapper.selectById(id);
        if (entity == null) return null;
        List<String> sources = sourceMapper.selectList(
                new QueryWrapper<GithubSyncTargetSource>()
                        .eq("target_id", id)
                        .orderByAsc("source_id")
        ).stream().map(GithubSyncTargetSource::getSourceId).toList();
        return TargetRow.from(entity).toView(sources);
    }

    public List<TargetView> enabledTargets() {
        return list().stream().filter(TargetView::enabled).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView create(TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        NormalizedTarget target = normalize(input);
        try {
            GithubSyncTarget entity = new GithubSyncTarget()
                    .setName(target.name())
                    .setRepositoryOwner(target.owner())
                    .setRepositoryName(target.repository())
                    .setBranchName(target.branch())
                    .setFilePath(target.filePath())
                    .setEnabled(target.enabled())
                    .setCreatedBy(operatorId)
                    .setUpdatedBy(operatorId);
            targetMapper.insert(entity);
            if (entity.getId() == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unable to create GitHub sync target");
            }
            replaceSources(entity.getId(), target.sourceIds());
            return Objects.requireNonNull(find(entity.getId()));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "A target already writes to this repository, branch, and file"
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView update(long id, TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        requireTarget(id);
        NormalizedTarget target = normalize(input);
        try {
            GithubSyncTarget entity = requireTargetEntity(id)
                    .setName(target.name())
                    .setRepositoryOwner(target.owner())
                    .setRepositoryName(target.repository())
                    .setBranchName(target.branch())
                    .setFilePath(target.filePath())
                    .setEnabled(target.enabled())
                    .setUpdatedAt(OffsetDateTime.now())
                    .setUpdatedBy(operatorId);
            targetMapper.updateById(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "A target already writes to this repository, branch, and file"
            );
        }
        replaceSources(id, target.sourceIds());
        return Objects.requireNonNull(find(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) throws BusinessException {
        if (targetMapper.deleteById(id) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GitHub sync target not found");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void seedLegacyTargetIfNeeded(GithubSyncSettingsService settingsService)
            throws BusinessException {
        if (databaseConfigService.exists(GithubSyncSettingsService.TARGETS_INITIALIZED_KEY)) {
            return;
        }
        if (count() == 0) {
            GithubSyncSettingsService.LegacyTargetDefaults defaults =
                    settingsService.legacyTargetDefaults();
            List<GithubRuleSourceCatalog.Definition> legacySources = sourceCatalog.definitions().stream()
                    .filter(definition -> !"manual-rules".equals(definition.id()))
                    .filter(definition -> !"mass-download".equals(definition.id()))
                    .filter(definition -> !"datacenter-highrisk".equals(definition.id()))
                    .filter(definition -> !"geoip-city-blacklist".equals(definition.id()))
                    .filter(definition -> !"btnbypass-userapps".equals(definition.id()))
                    .filter(definition -> sourceCatalog.isAvailable(definition.id()))
                    .toList();
            for (GithubRuleSourceCatalog.Definition definition : legacySources) {
                create(new TargetInput(
                        "Legacy " + definition.id(),
                        defaults.owner(),
                        defaults.repository(),
                        defaults.branch(),
                        definition.defaultFileName(),
                        true,
                        List.of(definition.id())
                ), null);
            }
        }
        databaseConfigService.insertIfMissing(
                GithubSyncSettingsService.TARGETS_INITIALIZED_KEY,
                "true",
                "migration"
        );
    }

    public void markAttempt(long id) {
        targetMapper.update(null,
                new LambdaUpdateWrapper<GithubSyncTarget>()
                        .set(GithubSyncTarget::getLastAttemptAt, OffsetDateTime.now())
                        .set(GithubSyncTarget::getLastError, null)
                        .eq(GithubSyncTarget::getId, id)
        );
    }

    public void markSuccess(long id, @Nullable String commitSha) {
        targetMapper.update(null,
                new LambdaUpdateWrapper<GithubSyncTarget>()
                        .set(GithubSyncTarget::getLastSuccessAt, OffsetDateTime.now())
                        .set(GithubSyncTarget::getLastError, null)
                        .set(GithubSyncTarget::getLastCommitSha, commitSha)
                        .eq(GithubSyncTarget::getId, id)
        );
    }

    public void markFailure(long id, String error) {
        targetMapper.update(null,
                new LambdaUpdateWrapper<GithubSyncTarget>()
                        .set(GithubSyncTarget::getLastError, sanitizeError(error))
                        .eq(GithubSyncTarget::getId, id)
        );
    }

    private long count() {
        return targetMapper.selectCount(null);
    }

    private void requireTarget(long id) throws BusinessException {
        if (targetMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GitHub sync target not found");
        }
    }

    private GithubSyncTarget requireTargetEntity(long id) throws BusinessException {
        GithubSyncTarget target = targetMapper.selectById(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GitHub sync target not found");
        }
        return target;
    }

    private Map<Long, List<String>> loadSources() {
        Map<Long, List<String>> sources = new LinkedHashMap<>();
        sourceMapper.selectList(
                new QueryWrapper<GithubSyncTargetSource>()
                        .orderByAsc("target_id")
                        .orderByAsc("source_id")
        ).forEach(entity -> sources.computeIfAbsent(
                entity.getTargetId(),
                ignored -> new ArrayList<>()
        ).add(entity.getSourceId()));
        return sources;
    }

    private void replaceSources(long targetId, List<String> sourceIds) {
        sourceMapper.delete(
                new QueryWrapper<GithubSyncTargetSource>()
                        .eq("target_id", targetId)
        );
        sourceIds.forEach(sourceId -> sourceMapper.insert(
                new GithubSyncTargetSource()
                        .setTargetId(targetId)
                        .setSourceId(sourceId)
        ));
    }

    private NormalizedTarget normalize(TargetInput input) throws BusinessException {
        if (input == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Target is required");
        }
        String name = trimmed(input.name(), "Target name", 128);
        String owner = trimmed(input.repositoryOwner(), "Repository owner", 100);
        String repository = trimmed(input.repositoryName(), "Repository name", 100);
        String branch = trimmed(input.branchName(), "Branch name", 255);
        String filePath = trimmed(input.filePath(), "File path", 1_024);
        if (!REPOSITORY_PART.matcher(owner).matches()
                || !REPOSITORY_PART.matcher(repository).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Repository owner and name contain unsupported characters");
        }
        if (!BRANCH_NAME.matcher(branch).matches()
                || branch.startsWith("/")
                || branch.endsWith("/")
                || branch.contains("..")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Branch name is invalid");
        }
        if (filePath.startsWith("/")
                || filePath.endsWith("/")
                || filePath.contains("..")
                || filePath.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "File path is invalid");
        }
        List<String> sourceIds = input.sourceIds() == null
                ? List.of()
                : input.sourceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (sourceIds.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Select at least one rule source");
        }
        for (String sourceId : sourceIds) {
            if (!sourceCatalog.sourceIds().contains(sourceId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Unknown rule source: " + sourceId);
            }
            if (!sourceCatalog.isAvailable(sourceId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Rule source is unavailable: " + sourceId);
            }
        }
        return new NormalizedTarget(
                name,
                owner,
                repository,
                branch,
                filePath,
                input.enabled(),
                sourceIds
        );
    }

    private String trimmed(String value, String label, int maxLength)
            throws BusinessException {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, label + " is invalid");
        }
        return normalized;
    }

    private String sanitizeError(String error) {
        String normalized = error == null ? "Unknown error" : error;
        return normalized.length() <= 1_000 ? normalized : normalized.substring(0, 1_000);
    }

    private record NormalizedTarget(
            String name,
            String owner,
            String repository,
            String branch,
            String filePath,
            boolean enabled,
            List<String> sourceIds
    ) {
    }

    private record TargetRow(
            long id,
            String name,
            String owner,
            String repository,
            String branch,
            String filePath,
            boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime lastSuccessAt,
            String lastError,
            String lastCommitSha
    ) {
        private static TargetRow from(GithubSyncTarget entity) {
            return new TargetRow(
                    entity.getId(),
                    entity.getName(),
                    entity.getRepositoryOwner(),
                    entity.getRepositoryName(),
                    entity.getBranchName(),
                    entity.getFilePath(),
                    Boolean.TRUE.equals(entity.getEnabled()),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt(),
                    entity.getLastAttemptAt(),
                    entity.getLastSuccessAt(),
                    entity.getLastError(),
                    entity.getLastCommitSha()
            );
        }

        private TargetView toView(List<String> sourceIds) {
            return new TargetView(
                    id,
                    name,
                    owner,
                    repository,
                    branch,
                    filePath,
                    enabled,
                    sourceIds,
                    createdAt,
                    updatedAt,
                    lastAttemptAt,
                    lastSuccessAt,
                    lastError,
                    lastCommitSha
            );
        }
    }
}
