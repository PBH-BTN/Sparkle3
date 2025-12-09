package com.ghostchu.btn.sparkle.controller.ui.swarmtracker;

import java.net.InetAddress;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.swarmtracker.dto.SwarmTrackerDto;
import com.ghostchu.btn.sparkle.entity.SwarmTracker;
import com.ghostchu.btn.sparkle.service.ISwarmTrackerService;
import com.ghostchu.btn.sparkle.service.ITorrentService;
import com.ghostchu.btn.sparkle.util.InfoHashUtil;

@Controller
public class SwarmTrackerViewController {

    @Autowired
    private ISwarmTrackerService swarmTrackerService;

    @Autowired
    private ITorrentService torrentService;

    @GetMapping("/swarm-tracker")
    public String swarmTrackerIndex(
            Model model,
            @RequestParam(required = false) String infoHash,
            @RequestParam(required = false) String peerIp,
            @RequestParam(required = false) Integer peerPort,
            @RequestParam(required = false) String peerId,
            @RequestParam(required = false) String peerClientName,
            @RequestParam(required = false) Double peerProgress,
            @RequestParam(required = false) Long fromPeerTraffic,
            @RequestParam(required = false) Long toPeerTraffic,
            @RequestParam(required = false) String flags,
            @RequestParam(required = false) String firstTimeSeenAfter,
            @RequestParam(required = false) String lastTimeSeenAfter,
            @RequestParam(required = false) Double userProgress,
            @RequestParam(required = false, defaultValue = "last_time_seen") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "100") int pageSize) {

        // 限制页大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 100;
        }

        // 处理 InfoHash 到 torrentId 的转换
        Long torrentId = null;
        if (infoHash != null && !infoHash.isBlank()) {
            String torrentIdentifier = InfoHashUtil.getHashedIdentifier(infoHash);
            var torrent = torrentService.getTorrentByTorrentIdentifier(torrentIdentifier);
            if (torrent != null) {
                torrentId = torrent.getId();
            }
        }

        // 解析 InetAddress
        InetAddress peerIpAddr = null;
        if (peerIp != null && !peerIp.isBlank()) {
            try {
                peerIpAddr = InetAddress.ofLiteral(peerIp);
            } catch (Exception e) {
                // 如果解析失败，保持为 null
            }
        }

        // 解析时间
        OffsetDateTime firstTimeSeenAfterDate = null;
        if (firstTimeSeenAfter != null && !firstTimeSeenAfter.isBlank()) {
            try {
                firstTimeSeenAfterDate = OffsetDateTime.parse(firstTimeSeenAfter);
            } catch (Exception e) {
                // 如果解析失败，保持为 null
            }
        }

        OffsetDateTime lastTimeSeenAfterDate = null;
        if (lastTimeSeenAfter != null && !lastTimeSeenAfter.isBlank()) {
            try {
                lastTimeSeenAfterDate = OffsetDateTime.parse(lastTimeSeenAfter);
            } catch (Exception e) {
                // 如果解析失败，保持为 null
            }
        }

        // 创建分页对象
        Page<SwarmTracker> pageObj = new Page<>(page, pageSize);

        // 查询数据
        var result = swarmTrackerService.querySwarmTracker(
                torrentId,
                peerIpAddr,
                peerPort,
                peerId,
                peerClientName,
                peerProgress,
                fromPeerTraffic,
                toPeerTraffic,
                flags,
                firstTimeSeenAfterDate,
                lastTimeSeenAfterDate,
                userProgress,
                sortBy,
                sortOrder,
                pageObj
        );

        // 转换为DTO
        var records = result.getRecords().stream()
                .map(SwarmTrackerDto::new)
                .toList();

        // 添加到模型
        model.addAttribute("records", records);
        model.addAttribute("total", result.getTotal());
        model.addAttribute("currentPage", result.getCurrent());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("totalPages", result.getPages());

        // 保持查询参数
        model.addAttribute("infoHash", infoHash);
        model.addAttribute("peerIp", peerIp);
        model.addAttribute("peerPort", peerPort);
        model.addAttribute("peerId", peerId);
        model.addAttribute("peerClientName", peerClientName);
        model.addAttribute("peerProgress", peerProgress);
        model.addAttribute("fromPeerTraffic", fromPeerTraffic);
        model.addAttribute("toPeerTraffic", toPeerTraffic);
        model.addAttribute("flags", flags);
        model.addAttribute("firstTimeSeenAfter", firstTimeSeenAfter);
        model.addAttribute("lastTimeSeenAfter", lastTimeSeenAfter);
        model.addAttribute("userProgress", userProgress);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        return "swarmtracker/index";
    }
}
