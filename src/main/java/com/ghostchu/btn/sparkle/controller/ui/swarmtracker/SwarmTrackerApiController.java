package com.ghostchu.btn.sparkle.controller.ui.swarmtracker;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto.SwarmTrackerDto;
import com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto.SwarmTrackerQueryDto;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.util.InfoHashUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Swarm Tracker API Controller
 * RESTful API for querying swarm tracker data
 */
@RestController
@RequestMapping("/api/swarmtracker")
public class SwarmTrackerApiController {

    @Autowired
    private ISwarmTrackerService swarmTrackerService;

    @Autowired
    private ITorrentService torrentService;

    /**
     * Query swarm tracker with filters and pagination
     */
    @PostMapping("/query")
    public SwarmTrackerPageResponse querySwarmTracker(@RequestBody SwarmTrackerQueryDto queryDto) {
        // 限制页大小
        int pageSize = queryDto.getSize();
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 100;
        }

        // 处理 InfoHash 到 torrentId 的转换
        Long torrentId = null;
        if (queryDto.getInfoHash() != null && !queryDto.getInfoHash().isBlank()) {
            String torrentIdentifier = InfoHashUtil.getHashedIdentifier(queryDto.getInfoHash());
            var torrent = torrentService.getTorrentByTorrentIdentifier(torrentIdentifier);
            if (torrent != null) {
                torrentId = torrent.getId();
            }
        }

        // 解析时间
        OffsetDateTime firstTimeSeenAfterDate = null;
        if (queryDto.getFirstTimeSeenAfter() != null && !queryDto.getFirstTimeSeenAfter().isBlank()) {
            try {
                firstTimeSeenAfterDate = OffsetDateTime.parse(queryDto.getFirstTimeSeenAfter());
            } catch (Exception e) {
                // 如果解析失败，保持为 null
            }
        }

        OffsetDateTime lastTimeSeenAfterDate = null;
        if (queryDto.getLastTimeSeenAfter() != null && !queryDto.getLastTimeSeenAfter().isBlank()) {
            try {
                lastTimeSeenAfterDate = OffsetDateTime.parse(queryDto.getLastTimeSeenAfter());
            } catch (Exception e) {
                // 如果解析失败，保持为 null
            }
        }

        // 创建分页对象
        Page<SwarmTracker> pageObj = new Page<>(queryDto.getPage(), pageSize);

        // 查询数据 - peerIp 直接传递字符串，支持 CIDR
        IPage<SwarmTracker> page = swarmTrackerService.querySwarmTracker(
                torrentId,
                queryDto.getPeerIp(),
                queryDto.getPeerPort(),
                queryDto.getPeerId(),
                queryDto.getPeerClientName(),
                queryDto.getPeerProgress(),
                queryDto.getFromPeerTraffic(),
                queryDto.getToPeerTraffic(),
                queryDto.getFlags(),
                firstTimeSeenAfterDate,
                lastTimeSeenAfterDate,
                queryDto.getUserProgress(),
                queryDto.getSortBy(),
                queryDto.getSortOrder(),
                pageObj
        );

        List<SwarmTrackerDto> records = page.getRecords().stream()
                .map(SwarmTrackerDto::new)
                .toList();

        return new SwarmTrackerPageResponse(
                records,
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SwarmTrackerPageResponse {
        private List<SwarmTrackerDto> records;
        private long current;
        private long size;
        private long total;
        private long pages;
    }
}
