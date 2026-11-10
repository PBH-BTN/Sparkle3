package com.ghostchu.btn.sparkle.controller.admin;

import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeletePreview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.BanHistoryDeleteResult;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.StorageOverview;
import com.ghostchu.btn.sparkle.controller.admin.dto.StorageModels.TableStorageView;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.AdminStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/storage")
@RequiredArgsConstructor
public class AdminStorageController {
    private final AdminStorageService storageService;

    @GetMapping("/tables")
    public StorageOverview tables() {
        return storageService.overview();
    }

    @PutMapping("/hypertables/{schema}/{table}/compression-policy")
    public TableStorageView updateCompressionPolicy(
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody CompressionPolicyRequest request
    ) throws BusinessException {
        return storageService.updateCompressionPolicy(
                schema,
                table,
                request.enabled(),
                request.compressAfterDays(),
                request.scheduleHours(),
                request.segmentBy(),
                request.orderBy()
        );
    }

    @PutMapping("/hypertables/{schema}/{table}/retention-policy")
    public TableStorageView updateRetentionPolicy(
            @PathVariable String schema,
            @PathVariable String table,
            @RequestBody RetentionPolicyRequest request
    ) throws BusinessException {
        return storageService.updateRetentionPolicy(
                schema,
                table,
                request.enabled(),
                request.dropAfterDays(),
                request.scheduleHours()
        );
    }

    @PostMapping("/ban-history/preview-delete")
    public BanHistoryDeletePreview previewBanHistoryDelete(@RequestBody CutoffRequest request)
            throws BusinessException {
        return storageService.previewBanHistoryDelete(request.cutoff());
    }

    @PostMapping("/ban-history/delete")
    public BanHistoryDeleteResult deleteBanHistory(@RequestBody DeleteRequest request)
            throws BusinessException {
        return storageService.deleteBanHistory(request.cutoff(), request.confirmation());
    }

    public record CompressionPolicyRequest(
            boolean enabled,
            int compressAfterDays,
            int scheduleHours,
            String segmentBy,
            String orderBy
    ) {
    }

    public record RetentionPolicyRequest(
            boolean enabled,
            int dropAfterDays,
            int scheduleHours
    ) {
    }

    public record CutoffRequest(OffsetDateTime cutoff) {
    }

    public record DeleteRequest(OffsetDateTime cutoff, String confirmation) {
    }
}
