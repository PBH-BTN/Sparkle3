package com.ghostchu.btn.sparkle.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.btn.sparkle.entity.AnalyseRule;
import com.ghostchu.btn.sparkle.mapper.AnalyseRuleMapper;
import com.ghostchu.btn.sparkle.service.IAnalyseRuleService;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractAnalyseRuleServiceImpl extends ServiceImpl<AnalyseRuleMapper, AnalyseRule> implements IAnalyseRuleService {

    public AbstractAnalyseRuleServiceImpl() {
    }

    public abstract Pair<@Nullable String, @Nullable String> getGeneratedContent();
}
