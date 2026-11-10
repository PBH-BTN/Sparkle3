package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SwarmTrackerMapperXmlTest {
    @Test
    void geoIpUpsertStatementsParse() throws Exception {
        String resource = "mapper/SwarmTrackerMapper.xml";
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.upsert"
        ));
        assertNotNull(configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.SwarmTrackerMapper.batchUpsert"
        ));
    }
}
