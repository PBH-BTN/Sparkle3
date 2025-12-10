package com.ghostchu.btn.sparkle.controller.ui.banhistory;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryQueryDto;
import com.ghostchu.btn.sparkle.controller.ui.banhistory.dto.BanHistoryResponseDto;
import com.ghostchu.btn.sparkle.entity.BanHistory;
import com.ghostchu.btn.sparkle.service.IBanHistoryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ban History API Controller
 * RESTful API for querying ban history data
 */
@RestController
@RequestMapping("/api/banhistory")
public class BanHistoryApiController {

    @Autowired
    private IBanHistoryService banHistoryService;

    /**
     * Query ban history with filters and pagination
     */
    @PostMapping("/query")
    public BanHistoryPageResponse queryBanHistory(@RequestBody BanHistoryQueryDto queryDto) {
        IPage<BanHistory> page = banHistoryService.queryBanHistory(queryDto);
        
        List<BanHistoryResponseDto> records = page.getRecords().stream()
                .map(BanHistoryResponseDto::new)
                .toList();
        
        return new BanHistoryPageResponse(
                records,
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }

    /**
     * Get dropdown options for filters
     */
    @GetMapping("/options")
    public FilterOptions getFilterOptions() {
        return new FilterOptions(
                banHistoryService.getDistinctModuleNames()
        );
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BanHistoryPageResponse {
        private List<BanHistoryResponseDto> records;
        private long current;
        private long size;
        private long total;
        private long pages;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FilterOptions {
        private List<String> moduleNames;
    }
}
