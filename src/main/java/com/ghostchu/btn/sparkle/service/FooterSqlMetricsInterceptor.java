package com.ghostchu.btn.sparkle.service;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FooterSqlMetricsInterceptor implements InnerInterceptor {
    private final FooterMetricsService metricsService;

    @Override
    public void beforeQuery(Executor executor,
                            MappedStatement mappedStatement,
                            Object parameter,
                            RowBounds rowBounds,
                            ResultHandler resultHandler,
                            BoundSql boundSql) {
        metricsService.recordSqlQuery();
    }

    @Override
    public void beforeUpdate(Executor executor,
                             MappedStatement mappedStatement,
                             Object parameter) {
        metricsService.recordSqlQuery();
    }
}
