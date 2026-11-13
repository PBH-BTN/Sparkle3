package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmTrackerMapperXmlTest {
    @Test
    void geoIpUpsertStatementsParse() throws Exception {
        Configuration configuration = configuration();

        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.upsert"
        ));
        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.batchUpsert"
        ));
    }

    @Test
    void trafficSummaryAggregatesWholeScopeIntoOneRow() throws Exception {
        MappedStatement statement = configuration().getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.sumPeerIpTraffic"
        );
        BoundSql boundSql = statement.getBoundSql(Map.of(
                "afterTimestamp", OffsetDateTime.now().minusHours(1),
                "peerIp", "114.224.127.0/24"
        ));

        String sql = normalize(boundSql.getSql());
        assertFalse(sql.contains("GROUP BY"),
                "CIDR 查询必须汇总范围内全部 peer 的流量，不能按 peer_ip 分组返回多行");
        assertTrue(sql.contains("SUM("));
    }

    private Configuration configuration() throws Exception {
        String resource = "mapper/SwarmTrackerMapper.xml";
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toUpperCase();
    }
}
