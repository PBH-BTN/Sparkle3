package com.ghostchu.btn.sparkle.converter;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps an already-serialized JSON document to PostgreSQL jsonb without
 * wrapping it in a JSON string literal.
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            String parameter,
            JdbcType jdbcType
    ) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(parameter);
        statement.setObject(index, json);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName)
            throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex)
            throws SQLException {
        return resultSet.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return statement.getString(columnIndex);
    }
}
