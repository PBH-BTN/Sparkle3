package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.dto.SubmissionModels.SubmissionRecord;
import com.ghostchu.btn.sparkle.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SubmissionService {
    private final SubmissionMapper mapper;

    public IPage<SubmissionRecord> query(long owner, String type, int days, int page, int size) {
        if (!Set.of("bans", "clients", "swarm").contains(type)
                || !Set.of(1, 3, 7).contains(days) || page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid query options");
        }
        var applicationIds = mapper.findApplicationIds(owner);
        if (applicationIds.isEmpty()) {
            return new Page<SubmissionRecord>(page, size, 0).setRecords(java.util.List.of());
        }
        OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC);
        return mapper.query(new Page<>(page, size), owner, applicationIds, type, end.minusDays(days), end);
    }
}
