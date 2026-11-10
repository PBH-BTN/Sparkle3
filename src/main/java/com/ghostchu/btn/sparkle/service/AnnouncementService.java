package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.PublicAnnouncementView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AnnouncementService {
    private static final Set<String> LEVELS = Set.of("GREEN", "YELLOW", "BLUE", "RED");
    private static final Set<String> PLACEMENTS = Set.of("GLOBAL_TOP", "HOME_HERO");
    private static final RowMapper<AnnouncementView> VIEW_MAPPER = (resultSet, rowNum) ->
            new AnnouncementView(
                    resultSet.getLong("id"),
                    resultSet.getString("level"),
                    resultSet.getString("placement"),
                    resultSet.getString("title_zh"),
                    resultSet.getString("title_en"),
                    resultSet.getString("content_zh"),
                    resultSet.getString("content_en"),
                    resultSet.getBoolean("enabled"),
                    resultSet.getBoolean("dismissible"),
                    resultSet.getInt("sort_order"),
                    resultSet.getObject("starts_at", OffsetDateTime.class),
                    resultSet.getObject("ends_at", OffsetDateTime.class),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class),
                    resultSet.getObject("updated_by", Long.class)
            );

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public @NotNull List<AnnouncementView> listAll() {
        return jdbcTemplate.query("""
                SELECT *
                FROM announcements
                ORDER BY sort_order DESC, updated_at DESC, id DESC
                """, VIEW_MAPPER);
    }

    public @NotNull List<PublicAnnouncementView> listActive(
            @Nullable String requestedPlacement
    ) throws BusinessException {
        String placement = requestedPlacement == null || requestedPlacement.isBlank()
                ? "GLOBAL_TOP"
                : normalizePlacement(requestedPlacement);
        return jdbcTemplate.query("""
                SELECT id, level, placement, title_zh, title_en, content_zh, content_en,
                       dismissible, starts_at, ends_at, updated_at
                FROM announcements
                WHERE enabled = TRUE
                  AND placement = ?
                  AND (starts_at IS NULL OR starts_at <= now())
                  AND (ends_at IS NULL OR ends_at > now())
                ORDER BY sort_order DESC, updated_at DESC, id DESC
                """, (resultSet, rowNum) -> new PublicAnnouncementView(
                resultSet.getLong("id"),
                resultSet.getString("level"),
                resultSet.getString("placement"),
                resultSet.getString("title_zh"),
                resultSet.getString("title_en"),
                resultSet.getString("content_zh"),
                resultSet.getString("content_en"),
                resultSet.getBoolean("dismissible"),
                resultSet.getObject("starts_at", OffsetDateTime.class),
                resultSet.getObject("ends_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        ), placement);
    }

    public @NotNull AnnouncementView create(
            @NotNull AnnouncementInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        AnnouncementInput normalized = validate(input);
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO announcements (
                    level, placement, title_zh, title_en, content_zh, content_en,
                    enabled, dismissible, sort_order, starts_at, ends_at, updated_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                normalized.level(),
                normalized.placement(),
                normalized.titleZh(),
                normalized.titleEn(),
                normalized.contentZh(),
                normalized.contentEn(),
                normalized.enabled(),
                normalized.dismissible(),
                normalized.sortOrder(),
                normalized.startsAt(),
                normalized.endsAt(),
                operatorId
        );
        if (id == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create announcement");
        }
        return require(id);
    }

    public @NotNull AnnouncementView update(
            long id,
            @NotNull AnnouncementInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        AnnouncementInput normalized = validate(input);
        int changed = jdbcTemplate.update("""
                UPDATE announcements
                SET level = ?,
                    placement = ?,
                    title_zh = ?,
                    title_en = ?,
                    content_zh = ?,
                    content_en = ?,
                    enabled = ?,
                    dismissible = ?,
                    sort_order = ?,
                    starts_at = ?,
                    ends_at = ?,
                    updated_at = now(),
                    updated_by = ?
                WHERE id = ?
                """,
                normalized.level(),
                normalized.placement(),
                normalized.titleZh(),
                normalized.titleEn(),
                normalized.contentZh(),
                normalized.contentEn(),
                normalized.enabled(),
                normalized.dismissible(),
                normalized.sortOrder(),
                normalized.startsAt(),
                normalized.endsAt(),
                operatorId,
                id
        );
        if (changed == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        return require(id);
    }

    public void delete(long id) throws BusinessException {
        int changed = jdbcTemplate.update("DELETE FROM announcements WHERE id = ?", id);
        if (changed == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
    }

    private AnnouncementView require(long id) throws BusinessException {
        List<AnnouncementView> values = jdbcTemplate.query(
                "SELECT * FROM announcements WHERE id = ?",
                VIEW_MAPPER,
                id
        );
        if (values.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        return values.getFirst();
    }

    private AnnouncementInput validate(AnnouncementInput input) throws BusinessException {
        String titleZh = normalize(input.titleZh());
        String titleEn = normalize(input.titleEn());
        String contentZh = normalize(input.contentZh());
        String contentEn = normalize(input.contentEn());
        if (titleZh.isBlank() && titleEn.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At least one localized announcement title is required");
        }
        if (contentZh.isBlank() && contentEn.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "At least one localized announcement message is required");
        }
        if (titleZh.length() > 500 || titleEn.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Announcement title is too long");
        }
        if (contentZh.length() > 20_000 || contentEn.length() > 20_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Announcement message is too long");
        }
        if (input.startsAt() != null
                && input.endsAt() != null
                && !input.endsAt().isAfter(input.startsAt())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Announcement end time must be after its start time");
        }
        if (input.sortOrder() < -10_000 || input.sortOrder() > 10_000) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid announcement sort order");
        }
        return new AnnouncementInput(
                normalizeLevel(input.level()),
                normalizePlacement(input.placement()),
                titleZh,
                titleEn,
                contentZh,
                contentEn,
                input.enabled(),
                input.dismissible(),
                input.sortOrder(),
                input.startsAt(),
                input.endsAt()
        );
    }

    private String normalizeLevel(String level) {
        String normalized = level == null ? "BLUE" : level.toUpperCase(Locale.ROOT);
        return LEVELS.contains(normalized) ? normalized : "BLUE";
    }

    private String normalizePlacement(String placement) throws BusinessException {
        String normalized = placement == null
                ? "GLOBAL_TOP"
                : placement.trim().toUpperCase(Locale.ROOT);
        if (!PLACEMENTS.contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Unsupported announcement placement");
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

}
