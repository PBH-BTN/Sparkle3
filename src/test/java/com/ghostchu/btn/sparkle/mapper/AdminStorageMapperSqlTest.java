package com.ghostchu.btn.sparkle.mapper;

import com.ghostchu.btn.sparkle.mapper.AdminStorageMapper.SqlOptionValue;
import com.ghostchu.btn.sparkle.mapper.AdminStorageMapper.SqlRelation;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminStorageMapperSqlTest {
    @Test
    void rendersValidatedDynamicColumnstoreOptions() {
        Configuration configuration = new Configuration();
        configuration.addMapper(AdminStorageMapper.class);
        BoundSql boundSql = configuration.getMappedStatement(
                AdminStorageMapper.class.getName()
                        + ".setColumnstoreOptionsWithSegment"
        ).getBoundSql(Map.of(
                "relation", new SqlRelation("public", "ban_history"),
                "segmentBy", new SqlOptionValue("peer_ip"),
                "orderBy", new SqlOptionValue("insert_time DESC")
        ));

        String sql = boundSql.getSql();
        assertTrue(sql.contains("\"public\".\"ban_history\""));
        assertTrue(sql.contains("timescaledb.segmentby = 'peer_ip'"));
        assertTrue(sql.contains("timescaledb.orderby = 'insert_time DESC'"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void rejectsUnsafeDynamicStorageFragments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlOptionValue("insert_time; DROP TABLE ban_history")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new SqlRelation("public\0hidden", "ban_history")
        );
    }
}
