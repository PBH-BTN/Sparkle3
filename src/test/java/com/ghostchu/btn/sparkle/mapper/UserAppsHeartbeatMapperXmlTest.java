package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.converter.InetAddressTypeHandler;
import com.ghostchu.btn.sparkle.converter.JsonbTypeHandler;
import com.ghostchu.btn.sparkle.entity.UserappsHeartbeat;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserAppsHeartbeatMapperXmlTest {

    @Test
    void upsertResolvesGeoipTypeHandler() throws Exception {
        String resource = "mapper/UserappsHeartbeatMapper.xml";
        Configuration configuration = new Configuration();
        configuration.getTypeHandlerRegistry().register(InetAddressTypeHandler.class);

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        var statement = configuration.getMappedStatement(
                "com.ghostchu.btn.sparkle.mapper.UserAppsHeartbeatMapper.upsert"
        );
        var geoipMapping = statement.getBoundSql(new UserappsHeartbeat()).getParameterMappings().stream()
                .filter(mapping -> "geoip".equals(mapping.getProperty()))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(JsonbTypeHandler.class, geoipMapping.getTypeHandler());
    }
}
