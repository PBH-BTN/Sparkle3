package com.ghostchu.btn.sparkle.service.export;

import com.ghostchu.btn.sparkle.constants.NotificationCategory;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.ExportOptions;
import com.ghostchu.btn.sparkle.controller.ui.dto.DataExportModels.ExportRequest;
import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.DataExportJobMapper;
import com.ghostchu.btn.sparkle.service.IUserService;
import com.ghostchu.btn.sparkle.service.UserMessageService;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataExportService {
    public static final String URL = "/api/ui/exports/";
    private final DataExportJobMapper jobs;
    private final DataExportSettings settings;
    private final ExportQueries queries;
    private final IUserService users;
    private final UserMessageService messages;

    public ExportOptions options(long userId, String language) throws BusinessException {
        requireSupporter(userId);
        var config = settings.get();
        int available = Math.toIntExact(Math.max(0, config.dailySlots()
                - jobs.usedSlots(userId, now().toLocalDate())));
        String prompt = (english(language) ? config.promptEn() : config.promptZh())
                .replace("{export_slots_max}", config.dailySlots().toString())
                .replace("{export_slots_available}", Integer.toString(available))
                .replace("{export_rows_max}", config.maxRows().toString())
                .replace("{export_filesize_max}", config.maxFileMiB().toString());
        return new ExportOptions(available, prompt);
    }

    @Transactional(rollbackFor = Exception.class)
    public UUID enqueue(long userId, ExportRequest request, String ip, String userAgent) throws BusinessException {
        requireSupporter(userId);
        if (request.category() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "export_invalid_filters");
        }
        var requestedAt = now();
        String filters = queries.validate(request.category(), request.filters(), requestedAt);
        jobs.lockAdmission();
        var config = settings.get();
        if (jobs.usedSlots(userId, requestedAt.toLocalDate()) >= config.dailySlots()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "export_quota_exhausted");
        }
        long reserved = jobs.reservedBytes();
        if (reserved >= config.poolBytes() || config.maxBytes() > config.poolBytes() - reserved) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "export_pool_full");
        }
        var id = UUID.randomUUID();
        var job = new DataExportJob(id, userId, request.category(), filters, english(request.language()) ? "en" : "zh",
                requestedAt, config.maxRows(), config.maxBytes(), config.retentionHours(), "QUEUED", null, null, null);
        // The request IP comes from the same servlet boundary as other audit records.
        jobs.enqueue(job, requestedAt.toLocalDate(), ip, userAgent);
        return id;
    }

    @Transactional
    public DataExportJob claim() {
        jobs.lockAdmission();
        return jobs.claim();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(UUID id) {
        return jobs.renew(id) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean complete(DataExportJob job, XzCsvWriter.Result result, String token) throws BusinessException {
        var expires = now().plusHours(job.retentionHours());
        if (jobs.ready(job.id(), result.rows(), result.bytes(), result.truncated(), digest(token), expires) != 1) {
            return false;
        }
        boolean en = english(job.language());
        String link = URL + job.id() + "/download?token=" + token;
        String body = en
                ? "Your export has " + result.rows() + " records and is ready to download. It expires at " + expires + "."
                : "导出文件已就绪，共 " + result.rows() + " 条记录。有效期至 " + expires + "。";
        if (result.truncated()) body += en ? " The export reached a limit." : " 导出已达到单次上限。";
        messages.publishSystemToUser(job.userId(), NotificationCategory.DATA_EXPORT,
                en ? "Data export ready" : "数据导出已完成", body + "\n" + link, false);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void fail(DataExportJob job, boolean staleOnly) throws BusinessException {
        if (jobs.fail(job.id(), staleOnly) == 1 && users.getById(job.userId()) != null) {
            boolean en = english(job.language());
            messages.publishSystemToUser(job.userId(), NotificationCategory.DATA_EXPORT,
                    en ? "Data export failed" : "数据导出失败",
                    en ? "Your export could not be prepared. The export credit has been returned."
                            : "未能准备导出文件，已返还本次导出次数。", false);
        }
    }

    public DataExportJob authorizeDownload(long uid, UUID id, String token) throws BusinessException {
        requireSupporter(uid);
        var job = jobs.find(id, uid);
        if (job == null || token == null || !token.matches("[A-Za-z0-9_-]{43}")
                || !"READY".equals(job.status()) || job.downloadDigest() == null
                || !job.expiresAt().isAfter(now())
                || !MessageDigest.isEqual(job.downloadDigest().getBytes(StandardCharsets.US_ASCII),
                        digest(token).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "export_download_unavailable");
        }
        return job;
    }

    public void requireSupporter(long uid) throws BusinessException {
        User user = users.getById(uid);
        if (user == null || user.isSuspended() || !user.roleNames().contains("supporter")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "export_supporter_required");
        }
    }

    static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static boolean english(String language) {
        return language != null && language.startsWith("en");
    }

    private static String digest(String token) {
        return Hashing.sha256().hashString(token, StandardCharsets.US_ASCII).toString();
    }
}
