package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmDownloadStatisticsMapperXmlTest {
    @Test
    void statementsParse() throws Exception {
        String resource = "mapper/SwarmDownloadStatisticsMapper.xml";
        Configuration configuration = new Configuration();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            byte[] xml = input.readAllBytes();
            new XMLMapperBuilder(
                    new ByteArrayInputStream(xml),
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();

            String sql = new String(xml, StandardCharsets.UTF_8);
            assertTrue(sql.contains("DISTINCT ON"));
            assertTrue(sql.contains("swarm.last_time_seen DESC"));
            assertTrue(sql.contains("GREATEST(swarm.from_peer_traffic, 0)"));
            assertTrue(sql.contains("WHEN #{ipv4Prefix} = 32 THEN host(latest.peer_ip)"));
        }

        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmDownloadStatisticsMapper.selectCidrDownloads"
        ));
    }
}
