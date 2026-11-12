package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.GithubSyncModels.TargetView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.DatabaseConfigService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
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

    private final JdbcTemplate jdbcTemplate;
    private final GithubRuleSourceCatalog sourceCatalog;
    private final DatabaseConfigService databaseConfigService;

    public List<TargetView> list() {
        List<TargetRow> rows = jdbcTemplate.query("""
                SELECT id, name, repository_owner, repository_name, branch_name,
                       file_path, enabled, created_at, updated_at, last_attempt_at,
                       last_success_at, last_error, last_commit_sha
                FROM github_sync_targets
                ORDER BY name, id
                """, (rs, rowNum) -> new TargetRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("repository_owner"),
                rs.getString("repository_name"),
                rs.getString("branch_name"),
                rs.getString("file_path"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_attempt_at", OffsetDateTime.class),
                rs.getObject("last_success_at", OffsetDateTime.class),
                rs.getString("last_error"),
                rs.getString("last_commit_sha")
        ));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> sources = loadSources();
        return rows.stream()
                .map(row -> row.toView(sources.getOrDefault(row.id(), List.of())))
                .toList();
    }

    public @Nullable TargetView find(long id) {
        return list().stream().filter(target -> target.id() == id).findFirst().orElse(null);
    }

    public List<TargetView> enabledTargets() {
        return list().stream().filter(TargetView::enabled).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView create(TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        NormalizedTarget target = normalize(input);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO github_sync_targets
                            (name, repository_owner, repository_name, branch_name, file_path,
                             enabled, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, target.name());
                statement.setString(2, target.owner());
                statement.setString(3, target.repository());
                statement.setString(4, target.branch());
                statement.setString(5, target.filePath());
                statement.setBoolean(6, target.enabled());
                statement.setObject(7, operatorId);
                statement.setObject(8, operatorId);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "A target already writes to this repository, branch, and file"
            );
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create GitHub sync target");
        }
        replaceSources(key.longValue(), target.sourceIds());
        return Objects.requireNonNull(find(key.longValue()));
    }

    @Transactional(rollbackFor = Exception.class)
    public TargetView update(long id, TargetInput input, @Nullable Long operatorId)
            throws BusinessException {
        requireTarget(id);
        NormalizedTarget target = normalize(input);
        try {
            jdbcTemplate.update("""
                    UPDATE github_sync_targets
                    SET name = ?,
                        repository_owner = ?,
                        repository_name = ?,
                        branch_name = ?,
                        file_path = ?,
                        enabled = ?,
                        updated_at = now(),
                        updated_by = ?
                    WHERE id = ?
                    """,
                    target.name(),
                    target.owner(),
                    target.repository(),
                    target.branch(),
                    target.filePath(),
                    target.enabled(),
                    operatorId,
                    id
            );
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
        if (jdbcTemplate.update("DELETE FROM github_sync_targets WHERE id = ?", id) == 0) {
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
        jdbcTemplate.update("""
                UPDATE github_sync_targets
                SET last_attempt_at = now(),
                    last_error = NULL
                WHERE id = ?
                """, id);
    }

    public void markSuccess(long id, @Nullable String commitSha) {
        jdbcTemplate.update("""
                UPDATE github_sync_targets
                SET last_success_at = now(),
                    last_error = NULL,
                    last_commit_sha = ?
                WHERE id = ?
                """, commitSha, id);
    }

    public void markFailure(long id, String error) {
        jdbcTemplate.update("""
                UPDATE github_sync_targets
                SET last_error = ?
                WHERE id = ?
                """, sanitizeError(error), id);
    }

    private long count() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM github_sync_targets",
                Long.class
        );
        return count == null ? 0 : count;
    }

    private void requireTarget(long id) throws BusinessException {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM github_sync_targets WHERE id = ?",
                Long.class,
                id
        );
        if (count == null || count == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GitHub sync target not found");
        }
    }

    private Map<Long, List<String>> loadSources() {
        Map<Long, List<String>> sources = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT target_id, source_id
                FROM github_sync_target_sources
                ORDER BY target_id, source_id
                """, rs -> {
            sources.computeIfAbsent(rs.getLong("target_id"), ignored -> new ArrayList<>())
                    .add(rs.getString("source_id"));
        });
        return sources;
    }

    private void replaceSources(long targetId, List<String> sourceIds) {
        jdbcTemplate.update(
                "DELETE FROM github_sync_target_sources WHERE target_id = ?",
                targetId
        );
        List<Object[]> values = sourceIds.stream()
                .map(sourceId -> new Object[]{targetId, sourceId})
                .toList();
        jdbcTemplate.batchUpdate(
                "INSERT INTO github_sync_target_sources (target_id, source_id) VALUES (?, ?)",
                values
        );
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
