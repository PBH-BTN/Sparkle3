package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.mapper.RuleMapper;
import com.ghostchu.btn.sparkle.service.IRuleService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Ghost_chu
 * @since 2025-11-29
 */
@Service
public class RuleServiceImpl extends ServiceImpl<RuleMapper, Rule> implements IRuleService {

    @Override
    public @NotNull List<Rule> getRulesByType(@NotNull String type) {
        return this.baseMapper.selectList(new QueryWrapper<Rule>()
                .eq("type", type));
    }

    @Nullable
    public String getIpDenyList() {
        return this.baseMapper.selectList(new QueryWrapper<Rule>()
                        .eq("type", "ip_denylist"))
                .stream().map(rule -> "# [Sparkle3 手动规则] " + rule.getCategory() + "\n" + rule.getContent())
                .collect(Collectors.joining("\n"));
    }
}
