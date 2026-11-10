package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HomeStatisticsMapperXmlTest {

    @Test
    void trendStatementsParse() throws Exception {
        String resource = "mapper/HomeStatisticsMapper.xml";
        Configuration configuration = new Configuration();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.HomeStatisticsMapper.selectClientNameTrend"
        ));
        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.HomeStatisticsMapper.selectGeoIpTrend"
        ));
    }
}
