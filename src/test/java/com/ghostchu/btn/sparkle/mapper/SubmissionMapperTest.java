package com.ghostchu.btn.sparkle.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionMapperTest {
    @Test
    void allSourcesHaveBoundOwnerAndTimeRestrictions() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/SubmissionMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var owned = configuration.getMappedStatement(SubmissionMapper.class.getName() + ".findApplicationIds")
                .getBoundSql(Map.of("owner", 42L));
        assertEquals("SELECT id FROM userapp WHERE owner = ?", owned.getSql().trim());
        var resultMap = configuration.getResultMap(SubmissionMapper.class.getName() + ".submissionRecord");
        assertTrue(resultMap.getResultMappings().stream().anyMatch(mapping ->
                mapping.getProperty().equals("peerGeoip") && mapping.getJavaType().getSimpleName().equals("IPGeoData")));
        assertTrue(resultMap.getResultMappings().stream().anyMatch(mapping ->
                mapping.getProperty().equals("structuredData") && mapping.getJavaType().equals(Map.class)));
        var statement = configuration.getMappedStatement(SubmissionMapper.class.getName() + ".query");
        String[] types = {"bans", "clients", "swarm"};
        String[] tables = {"ban_history", "client_discovery", "swarm_tracker"};
        String[] appColumns = {"userapps_id", "found_userapps_id", "userapps_id"};
        String[] times = {"populate_time", "found_at", "last_time_seen"};
        for (int i = 0; i < types.length; i++) {
            var bound = statement.getBoundSql(Map.of("type", types[i], "owner", 42L, "applicationIds", java.util.List.of(10L, 20L),
                    "start", OffsetDateTime.now().minusDays(3), "end", OffsetDateTime.now()));
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("FROM " + tables[i]));
            assertTrue(sql.matches("(?s).*AND " + appColumns[i] + " IN \\( *\\? *, *\\? *\\).*"));
            assertTrue(sql.contains(times[i] + " >= ? AND " + times[i] + " <= ?"));
            assertTrue(sql.contains("a.id = r.app AND a.owner = ?"));
            assertTrue(sql.contains("r.peer_port"));
            assertTrue(sql.contains("r.peer_progress"));
            assertTrue(sql.contains("r.structured_data"));
            if (i == 0) {
                assertFalse(sql.contains("WHERE insert_time"));
                assertTrue(sql.contains("populate_time AS submitted_at"));
                assertTrue(sql.contains("peer_flags AS flags"));
            }
            var empty = statement.getBoundSql(Map.of("type", types[i], "owner", 42L,
                    "applicationIds", java.util.List.of(), "start", OffsetDateTime.now(), "end", OffsetDateTime.now()));
            assertTrue(empty.getSql().contains("(NULL)"));
            assertEquals(java.util.List.of("start", "end", "__frch_appId_0", "__frch_appId_1", "owner"), bound.getParameterMappings()
                    .stream().map(mapping -> mapping.getProperty()).toList());
        }
    }
}
