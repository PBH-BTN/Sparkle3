package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.ClientDiscovery;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IClientDiscoveryService extends IService<ClientDiscovery> {

    void handleClientDiscovery(long userAppId, List<Pair<String, String>> data);
}
