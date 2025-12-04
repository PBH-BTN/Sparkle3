package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.btn.sparkle.entity.Rule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
public interface IRuleService extends IService<Rule> {

    @NotNull List<Rule> getRulesByType(@NotNull String type);
}
