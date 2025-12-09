package com.ghostchu.btn.sparkle.controller.ui.clientdiscovery;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto.ClientDiscoveryDto;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ClientDiscoveryViewController {

    @Autowired
    private IClientDiscoveryService clientDiscoveryService;

    @GetMapping("/client-discovery")
    public String clientDiscoveryIndex(
            Model model,
            @RequestParam(required = false) String peerId,
            @RequestParam(required = false) String peerClientName,
            @RequestParam(required = false) String clientType,
            @RequestParam(required = false) String clientSemver,
            @RequestParam(required = false, defaultValue = "found_at") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int pageSize) {

        // 限制页大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }

        // 创建分页对象
        Page<ClientDiscovery> pageObj = new Page<>(page, pageSize);

        // 查询数据
        var result = clientDiscoveryService.queryClientDiscovery(
                peerId,
                peerClientName,
                clientType,
                clientSemver,
                sortBy,
                sortOrder,
                pageObj
        );

        // 转换为DTO
        var records = result.getRecords().stream()
                .map(ClientDiscoveryDto::new)
                .toList();

        // 获取所有客户端类型用于下拉框
        List<String> clientTypes = clientDiscoveryService.getAllClientTypes();

        // 添加到模型
        model.addAttribute("records", records);
        model.addAttribute("total", result.getTotal());
        model.addAttribute("currentPage", result.getCurrent());
        model.addAttribute("pageSize", result.getSize());
        model.addAttribute("totalPages", result.getPages());
        model.addAttribute("clientTypes", clientTypes);

        // 保持查询参数
        model.addAttribute("peerId", peerId);
        model.addAttribute("peerClientName", peerClientName);
        model.addAttribute("clientType", clientType);
        model.addAttribute("clientSemver", clientSemver);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);

        return "clientdiscovery/index";
    }
}
