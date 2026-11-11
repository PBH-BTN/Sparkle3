package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyseMassDownloadMapperXmlTest {
    @Test
    void massDownloadStatementDeduplicatesAndFiltersLatestDownloads() throws Exception {
        String resource = "mapper/AnalyseRuleMapper.xml";
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

            String mapper = new String(xml, StandardCharsets.UTF_8);
            assertTrue(mapper.contains("swarm.last_time_seen DESC"));
            assertTrue(mapper.contains("GREATEST(swarm.to_peer_traffic, 0)"));
            assertTrue(mapper.contains("WHEN #{ipv4Prefix} = 32 THEN host(latest.peer_ip)"));
            assertTrue(mapper.contains("WHERE grouped.download_bytes &gt; #{thresholdDownload}"));
        }

        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.AnalyseRuleMapper.analyseMassDownload"
        ));
    }
}
