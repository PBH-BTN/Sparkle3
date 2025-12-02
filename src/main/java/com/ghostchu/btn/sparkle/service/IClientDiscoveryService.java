package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IClientDiscoveryService extends IService<ClientDiscovery> {

    void handleClientDiscovery(long userAppId, List<Map.Entry<String, String>> data);
}
