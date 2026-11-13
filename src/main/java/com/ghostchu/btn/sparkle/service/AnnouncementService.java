package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementInput;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.AnnouncementView;
import com.ghostchu.btn.sparkle.controller.admin.dto.AnnouncementModels.PublicAnnouncementView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.entity.Announcement;
import com.ghostchu.btn.sparkle.mapper.AnnouncementMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class AnnouncementService {
    private static final Set<String> LEVELS = Set.of("GREEN", "YELLOW", "BLUE", "RED");
    private static final Set<String> PLACEMENTS = Set.of("GLOBAL_TOP", "HOME_HERO");
    private final AnnouncementMapper announcementMapper;
    private final UserMessageService messageService;

    public AnnouncementService(
            AnnouncementMapper announcementMapper,
            UserMessageService messageService
    ) {
        this.announcementMapper = announcementMapper;
        this.messageService = messageService;
    }

    public @NotNull List<AnnouncementView> listAll() {
        return announcementMapper.selectList(
                new QueryWrapper<Announcement>()
                        .orderByDesc("sort_order")
                        .orderByDesc("updated_at")
                        .orderByDesc("id")
        ).stream().map(this::toView).toList();
    }

    public @NotNull List<PublicAnnouncementView> listActive(
            @Nullable String requestedPlacement
    ) throws BusinessException {
        String placement = requestedPlacement == null || requestedPlacement.isBlank()
                ? "GLOBAL_TOP"
                : normalizePlacement(requestedPlacement);
        OffsetDateTime now = OffsetDateTime.now();
        return announcementMapper.selectList(
                new QueryWrapper<Announcement>()
                        .eq("enabled", true)
                        .eq("placement", placement)
                        .and(wrapper -> wrapper.isNull("starts_at")
                                .or().le("starts_at", now))
                        .and(wrapper -> wrapper.isNull("ends_at")
                                .or().gt("ends_at", now))
                        .orderByDesc("sort_order")
                        .orderByDesc("updated_at")
                        .orderByDesc("id")
        ).stream().map(entity -> new PublicAnnouncementView(
                entity.getId(),
                entity.getLevel(),
                entity.getPlacement(),
                entity.getTitleZh(),
                entity.getTitleEn(),
                entity.getContentZh(),
                entity.getContentEn(),
                Boolean.TRUE.equals(entity.getDismissible()),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getUpdatedAt()
        )).toList();
    }

    public @NotNull AnnouncementView create(
            @NotNull AnnouncementInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        AnnouncementInput normalized = validate(input);
        Announcement entity = new Announcement()
                .setLevel(normalized.level())
                .setPlacement(normalized.placement())
                .setTitleZh(normalized.titleZh())
                .setTitleEn(normalized.titleEn())
                .setContentZh(normalized.contentZh())
                .setContentEn(normalized.contentEn())
                .setEnabled(normalized.enabled())
                .setDismissible(normalized.dismissible())
                .setSortOrder(normalized.sortOrder())
                .setStartsAt(normalized.startsAt())
                .setEndsAt(normalized.endsAt())
                .setUpdatedBy(operatorId);
        announcementMapper.insert(entity);
        if (entity.getId() == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to create announcement");
        }
        AnnouncementView created = require(entity.getId());
        if (created.enabled()) {
            publishInbox(created);
        }
        return created;
    }

    public @NotNull AnnouncementView update(
            long id,
            @NotNull AnnouncementInput input,
            @Nullable Long operatorId
    ) throws BusinessException {
        AnnouncementInput normalized = validate(input);
        AnnouncementView existing = require(id);
        Announcement entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        entity.setLevel(normalized.level())
                .setPlacement(normalized.placement())
                .setTitleZh(normalized.titleZh())
                .setTitleEn(normalized.titleEn())
                .setContentZh(normalized.contentZh())
                .setContentEn(normalized.contentEn())
                .setEnabled(normalized.enabled())
                .setDismissible(normalized.dismissible())
                .setSortOrder(normalized.sortOrder())
                .setStartsAt(normalized.startsAt())
                .setEndsAt(normalized.endsAt())
                .setUpdatedAt(OffsetDateTime.now())
                .setUpdatedBy(operatorId);
        announcementMapper.updateById(entity);
        AnnouncementView updated = require(id);
        if (!existing.enabled() && updated.enabled()) {
            publishInbox(updated);
        }
        return updated;
    }

    public void delete(long id) throws BusinessException {
        int changed = announcementMapper.deleteById(id);
        if (changed == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
    }

    private AnnouncementView require(long id) throws BusinessException {
        Announcement value = announcementMapper.selectById(id);
        if (value == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Announcement not found");
        }
        return toView(value);
    }

    private AnnouncementView toView(Announcement entity) {
        return new AnnouncementView(
                entity.getId(),
                entity.getLevel(),
                entity.getPlacement(),
                entity.getTitleZh(),
                entity.getTitleEn(),
                entity.getContentZh(),
                entity.getContentEn(),
                Boolean.TRUE.equals(entity.getEnabled()),
                Boolean.TRUE.equals(entity.getDismissible()),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
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

    private void publishInbox(AnnouncementView announcement) {
        String title = announcement.titleZh().isBlank()
                ? announcement.titleEn()
                : announcement.titleZh();
        String content = announcement.contentZh().isBlank()
                ? announcement.contentEn()
                : announcement.contentZh();
        NotificationCategory category = "RED".equals(announcement.level())
                ? NotificationCategory.MAJOR_ANNOUNCEMENT
                : NotificationCategory.DAILY_ANNOUNCEMENT;
        try {
            messageService.publishSystem(category, title, content, true);
        } catch (Exception exception) {
            log.warn("Unable to publish inbox notification for announcement {}: {}",
                    announcement.id(), exception.getMessage());
        }
    }

}
