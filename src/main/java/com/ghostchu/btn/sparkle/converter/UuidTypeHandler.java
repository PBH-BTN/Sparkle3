package com.ghostchu.btn.sparkle.converter;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UuidTypeHandler extends BaseTypeHandler<UUID> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            UUID parameter,
            JdbcType jdbcType
    ) throws SQLException {
        statement.setObject(index, parameter);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getObject(columnName, UUID.class);
    }

    @Override
    public UUID getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getObject(columnIndex, UUID.class);
    }

    @Override
    public UUID getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return statement.getObject(columnIndex, UUID.class);
    }
}
