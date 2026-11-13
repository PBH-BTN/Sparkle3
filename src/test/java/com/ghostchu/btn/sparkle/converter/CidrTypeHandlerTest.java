package com.ghostchu.btn.sparkle.converter;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ghostchu.btn.sparkle.autoconfig.MyBatisPlusConfig;
import com.ghostchu.btn.sparkle.entity.IpQueryComment;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CidrTypeHandlerTest {
    @Test
    void bindsValueWithCidrTypeInsteadOfVarchar() throws Exception {
        CidrTypeHandler handler = new CidrTypeHandler();
        PreparedStatement statement = mock(PreparedStatement.class);
        ArgumentCaptor<Object> parameter = ArgumentCaptor.forClass(Object.class);

        handler.setNonNullParameter(statement, 3, "192.0.2.0/24", JdbcType.OTHER);

        verify(statement).setObject(eq(3), parameter.capture());
        PGobject value = (PGobject) parameter.getValue();
        assertEquals("cidr", value.getType());
        assertEquals("192.0.2.0/24", value.getValue());
    }

    @Test
    void commentInsertBindsNetworkThroughTheCidrTypeHandler() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        new MyBatisPlusConfig().configurationCustomizer().customize(configuration);
        TableInfo tableInfo = TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, "cidr-type-handler-test"),
                IpQueryComment.class
        );
        TableFieldInfo network = tableInfo.getFieldList().stream()
                .filter(field -> "network".equals(field.getProperty()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "ip_query_comments.network must be mapped as an insertable column"
                ));

        assertTrue(network.getInsertSqlColumn().startsWith("network"));
        assertTrue(
                network.getInsertSqlProperty("et").contains("CidrTypeHandler"),
                "the insert must bind network with CidrTypeHandler, otherwise PostgreSQL"
                        + " receives varchar for the cidr column"
        );
    }

    @Test
    void readsCidrBackAsCanonicalText() throws Exception {
        CidrTypeHandler handler = new CidrTypeHandler();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("network")).thenReturn("2001:db8:1234:5600::/56");

        assertEquals(
                "2001:db8:1234:5600::/56",
                handler.getNullableResult(resultSet, "network")
        );
    }
}
