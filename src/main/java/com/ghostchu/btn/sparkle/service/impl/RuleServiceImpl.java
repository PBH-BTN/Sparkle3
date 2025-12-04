package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.Rule;
import com.ghostchu.btn.sparkle.mapper.RuleMapper;
import com.ghostchu.btn.sparkle.service.IRuleService;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @NotNull
    public List<Rule> getRulesByType(@NotNull String type) {
        return this.baseMapper.selectList(new QueryWrapper<Rule>()
                .eq("type", type));
    }

}
