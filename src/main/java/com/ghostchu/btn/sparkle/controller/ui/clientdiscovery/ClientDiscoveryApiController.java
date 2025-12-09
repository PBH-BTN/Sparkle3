package com.ghostchu.btn.sparkle.controller.ui.clientdiscovery;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto.ClientDiscoveryDto;
import com.ghostchu.btn.sparkle.controller.ui.clientdiscovery.dto.ClientDiscoveryQueryDto;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import com.ghostchu.btn.sparkle.service.IClientDiscoveryService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Client Discovery API Controller
 * RESTful API for querying client discovery data
 */
@RestController
@RequestMapping("/api/clientdiscovery")
public class ClientDiscoveryApiController {

    @Autowired
    private IClientDiscoveryService clientDiscoveryService;

    /**
     * Query client discovery with filters and pagination
     */
    @PostMapping("/query")
    public ClientDiscoveryPageResponse queryClientDiscovery(@RequestBody ClientDiscoveryQueryDto queryDto) {
        // 限制页大小
        int pageSize = queryDto.getSize();
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }

        // 创建分页对象
        Page<ClientDiscovery> pageObj = new Page<>(queryDto.getPage(), pageSize);

        // 查询数据
        IPage<ClientDiscovery> page = clientDiscoveryService.queryClientDiscovery(
                queryDto.getPeerId(),
                queryDto.getPeerClientName(),
                queryDto.getClientType(),
                queryDto.getClientSemver(),
                queryDto.getSortBy(),
                queryDto.getSortOrder(),
                pageObj
        );

        List<ClientDiscoveryDto> records = page.getRecords().stream()
                .map(ClientDiscoveryDto::new)
                .toList();

        return new ClientDiscoveryPageResponse(
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
                clientDiscoveryService.getAllClientTypes()
        );
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClientDiscoveryPageResponse {
        private List<ClientDiscoveryDto> records;
        private long current;
        private long size;
        private long total;
        private long pages;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FilterOptions {
        private List<String> clientTypes;
    }
}
