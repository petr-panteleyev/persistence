/*
 * Copyright (c) 2017, 2018, Petr Panteleyev <petr@panteleyev.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.panteleyev.persistence;

import org.panteleyev.persistence.annotations.Column;
import org.panteleyev.persistence.annotations.ForeignKey;
import org.panteleyev.persistence.annotations.Index;
import org.panteleyev.persistence.annotations.ReferenceOption;
import org.panteleyev.persistence.annotations.Table;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import static org.panteleyev.persistence.DAOTypes.BAD_FIELD_TYPE;
import static org.panteleyev.persistence.DAOTypes.TYPE_BIG_DECIMAL;
import static org.panteleyev.persistence.DAOTypes.TYPE_BOOL;
import static org.panteleyev.persistence.DAOTypes.TYPE_BOOLEAN;
import static org.panteleyev.persistence.DAOTypes.TYPE_DATE;
import static org.panteleyev.persistence.DAOTypes.TYPE_ENUM;
import static org.panteleyev.persistence.DAOTypes.TYPE_INT;
import static org.panteleyev.persistence.DAOTypes.TYPE_INTEGER;
import static org.panteleyev.persistence.DAOTypes.TYPE_LOCAL_DATE;
import static org.panteleyev.persistence.DAOTypes.TYPE_LONG;
import static org.panteleyev.persistence.DAOTypes.TYPE_LONG_PRIM;
import static org.panteleyev.persistence.DAOTypes.TYPE_STRING;

interface DAOProxy {
    BiFunction<ResultSet, String, Object> OBJECT_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getObject(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Boolean> BOOL_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getBoolean(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Boolean> INT_BOOL_READER = (ResultSet rs, String name) -> {
        try {
            Object value = rs.getObject(name);
            return value != null && ((int) value == 1);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Boolean> INT_BOOLEAN_READER = (ResultSet rs, String name) -> {
        try {
            Object value = rs.getObject(name);
            return value == null ? null : (int) value == 1;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, BigDecimal> BIG_DECIMAL_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getBigDecimal(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Integer> INT_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getInt(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Long> LONG_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getLong(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, Date> DATE_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getObject(name) == null ? null : new Date(rs.getLong(name));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, LocalDate> LOCAL_DATE_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getObject(name) == null ? null : LocalDate.ofEpochDay(rs.getLong(name));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    BiFunction<ResultSet, String, byte[]> BYTE_ARRAY_READER = (ResultSet rs, String name) -> {
        try {
            return rs.getBytes(name);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    };

    default Object getFieldValue(String fieldName, Class typeClass, ResultSet set) throws SQLException {
        if (typeClass.isEnum()) {
            var value = set.getObject(fieldName);
            return value == null ? null : Enum.valueOf(typeClass, (String) value);
        }

        var reader = getReaderMap().get(typeClass.getTypeName());
        if (reader == null) {
            throw new IllegalStateException(BAD_FIELD_TYPE);
        }

        return reader.apply(set, fieldName);
    }

    Map<String, BiFunction<ResultSet, String, ?>> getReaderMap();

    String getColumnString(Column fld, ForeignKey foreignKey, String typeName, List<String> constraints);

    void truncate(Connection connection, List<Class<? extends Record>> tables);

    default void setFieldData(PreparedStatement st, int index, Object value, String typeName) throws SQLException {
        switch (typeName) {
            case TYPE_STRING:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, (String) value);
                }
                break;
            case TYPE_BOOL:
            case TYPE_BOOLEAN:
                if (value == null) {
                    st.setNull(index, Types.BOOLEAN);
                } else {
                    st.setBoolean(index, (Boolean) value);
                }
                break;
            case TYPE_INTEGER:
            case TYPE_INT:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setInt(index, (Integer) value);
                }
                break;
            case TYPE_LONG:
            case TYPE_LONG_PRIM:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, (Long) value);
                }
                break;
            case TYPE_DATE:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, ((Date) value).getTime());
                }
                break;
            case TYPE_LOCAL_DATE:
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, ((LocalDate) value).toEpochDay());
                }
                break;
            case TYPE_BIG_DECIMAL:
                if (value == null) {
                    st.setNull(index, Types.DECIMAL);
                } else {
                    st.setBigDecimal(index, (BigDecimal) value);
                }
                break;
            case TYPE_ENUM:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, ((Enum) value).name());
                }
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE);
        }
    }

    default String buildForeignKey(Column column, ForeignKey key) {
        Objects.requireNonNull(key);

        Class<?> parentTableClass = key.table();
        if (!parentTableClass.isAnnotationPresent(Table.class)) {
            throw new IllegalStateException("Foreign key references not annotated table");
        }

        var parentTableName = parentTableClass.getAnnotation(Table.class).value();
        var parentFieldName = key.field();

        var fk = new StringBuilder();

        fk.append("FOREIGN KEY (")
                .append(column.value())
                .append(") ")
                .append("REFERENCES ")
                .append(parentTableName)
                .append("(")
                .append(parentFieldName)
                .append(")");

        if (key.onUpdate() != ReferenceOption.NONE) {
            fk.append(" ON UPDATE ").append(key.onUpdate().toString());
        }

        if (key.onDelete() != ReferenceOption.NONE) {
            fk.append(" ON DELETE ").append(key.onDelete().toString());
        }

        return fk.toString();
    }

    default String buildIndex(Table table, Field field) {
        var column = field.getAnnotation(Column.class);
        var index = field.getAnnotation(Index.class);

        var b = new StringBuilder("CREATE ");
        if (index.unique()) {
            b.append("UNIQUE ");
        }

        b.append("INDEX ")
                .append(index.value())
                .append(" ON ")
                .append(table.value())
                .append(" (")
                .append(column.value())
                .append(")");

        return b.toString();
    }

    default void deleteAll(Connection connection, Class<? extends Record> table) {
        try (var statement = connection.createStatement()) {
            statement.execute("DELETE FROM " + Record.getTableName(table));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
