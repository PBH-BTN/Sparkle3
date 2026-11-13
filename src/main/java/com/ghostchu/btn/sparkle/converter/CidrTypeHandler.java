package com.ghostchu.btn.sparkle.converter;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Binds a canonical CIDR string to a PostgreSQL cidr column. PostgreSQL does
 * not implicitly cast varchar parameters to cidr, so the value has to be sent
 * with the cidr type instead of as a plain string.
 */
public class CidrTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(
            PreparedStatement statement,
            int index,
            String parameter,
            JdbcType jdbcType
    ) throws SQLException {
        PGobject cidr = new PGobject();
        cidr.setType("cidr");
        cidr.setValue(parameter);
        statement.setObject(index, cidr);
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
