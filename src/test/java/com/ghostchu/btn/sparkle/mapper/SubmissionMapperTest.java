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
        var statement = configuration.getMappedStatement(SubmissionMapper.class.getName() + ".query");
        String[] types = {"bans", "clients", "swarm"};
        String[] tables = {"ban_history", "client_discovery", "swarm_tracker"};
        String[] times = {"insert_time", "found_at", "last_time_seen"};
        for (int i = 0; i < types.length; i++) {
            var bound = statement.getBoundSql(Map.of("type", types[i], "owner", 42L,
                    "start", OffsetDateTime.now().minusDays(3), "end", OffsetDateTime.now()));
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("FROM " + tables[i]));
            assertTrue(sql.contains(times[i] + " >= ? AND " + times[i] + " <= ?"));
            assertTrue(sql.contains("a.id = r.app AND a.owner = ?"));
            assertEquals(java.util.List.of("start", "end", "owner"), bound.getParameterMappings()
                    .stream().map(mapping -> mapping.getProperty()).toList());
        }
    }
}
