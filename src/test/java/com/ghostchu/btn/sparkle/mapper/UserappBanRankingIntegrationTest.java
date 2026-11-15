package com.ghostchu.btn.sparkle.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.entity.Userapp;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "SPARKLE_FLYWAY_TEST_URL", matches = ".+")
class UserappBanRankingIntegrationTest {
    @Test
    void ranksBeforePagingAndAppliesWindowAndApplicationFilters() throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("SPARKLE_FLYWAY_TEST_URL"),
                System.getenv("SPARKLE_FLYWAY_TEST_USER"), System.getenv("SPARKLE_FLYWAY_TEST_PASSWORD"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMP TABLE userapp (
                            id bigint, app_id text, comment text, owner bigint, created_at timestamptz,
                            last_seen_at timestamptz, banned_at timestamptz, banned_reason text,
                            btn_bypass boolean, installation_id text, last_user_agent text
                        );
                        CREATE TEMP TABLE ban_history (userapps_id bigint, populate_time timestamptz);
                        INSERT INTO userapp (id, app_id, owner, banned_at) VALUES
                            (1, 'active-a', 10, NULL),
                            (2, 'banned', 10, '2026-01-07 12:00Z'),
                            (3, 'active-b', 10, NULL),
                            (4, 'other-owner', 20, NULL);
                        INSERT INTO ban_history VALUES
                            (1, '2026-01-01 00:00Z'), (1, '2026-01-02 00:00Z'),
                            (1, '2026-01-05 00:00Z'), (1, '2026-01-08 00:00Z'),
                            (2, '2026-01-07 00:00Z'), (2, '2026-01-07 12:00Z'),
                            (3, '2025-12-31 23:59Z'), (99, '2026-01-07 12:00Z');
                        """);
            }
            var configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);
            var pagination = new MybatisPlusInterceptor();
            pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
            configuration.addInterceptor(pagination);
            String resource = "mapper/UserappMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var mapper = session.getMapper(UserappMapper.class);
                var end = OffsetDateTime.parse("2026-01-08T00:00:00Z");
                var owner = new QueryWrapper<Userapp>().eq("owner", 10L);
                var week = mapper.pageByBanCount(page(1, 2), owner, end.minusDays(7), end);
                assertEquals(3, week.getTotal());
                assertEquals(List.of(1L, 2L), week.getRecords().stream().map(Userapp::getId).toList());
                assertEquals(List.of(3L, 2L), week.getRecords().stream().map(Userapp::getBanCount).toList());

                var threeDays = mapper.pageByBanCount(page(1, 2), owner, end.minusDays(3), end);
                assertEquals(List.of(2L, 1L), threeDays.getRecords().stream().map(Userapp::getId).toList());
                assertEquals(List.of(2L, 1L), threeDays.getRecords().stream().map(Userapp::getBanCount).toList());

                var filtered = new QueryWrapper<Userapp>().eq("owner", 10L)
                        .like("app_id", "active").isNull("banned_at");
                var day = mapper.pageByBanCount(page(2, 1), filtered, end.minusDays(1), end);
                assertEquals(2, day.getTotal());
                assertEquals(List.of(1L), day.getRecords().stream().map(Userapp::getId).toList());
                assertEquals(List.of(0L), day.getRecords().stream().map(Userapp::getBanCount).toList());
            }
        }
    }

    private Page<Userapp> page(long current, long size) {
        var page = new Page<Userapp>(current, size);
        page.setCountId("countFilteredApplications");
        return page;
    }
}
