/*
 * Copyright (c) 2017, Petr Panteleyev <petr@panteleyev.org>
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

import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.ForeignKey;
import org.panteleyev.persistence.annotations.Index;
import org.panteleyev.persistence.annotations.ReferenceOption;
import org.panteleyev.persistence.annotations.Table;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import static org.panteleyev.persistence.DAOTypes.BAD_FIELD_TYPE;
import static org.panteleyev.persistence.DAOTypes.TYPE_BIG_DECIMAL;
import static org.panteleyev.persistence.DAOTypes.TYPE_BOOL;
import static org.panteleyev.persistence.DAOTypes.TYPE_BOOLEAN;
import static org.panteleyev.persistence.DAOTypes.TYPE_DATE;
import static org.panteleyev.persistence.DAOTypes.TYPE_ENUM;
import static org.panteleyev.persistence.DAOTypes.TYPE_INT;
import static org.panteleyev.persistence.DAOTypes.TYPE_INTEGER;
import static org.panteleyev.persistence.DAOTypes.TYPE_LONG;
import static org.panteleyev.persistence.DAOTypes.TYPE_LONG_PRIM;
import static org.panteleyev.persistence.DAOTypes.TYPE_STRING;

interface DAOProxy {
    Object getFieldValue(String fieldName, Class typeClass, ResultSet set) throws SQLException;
    String getColumnString(Field fld, ForeignKey foreignKey, String typeName, List<String> constraints);

    default void setFieldData(PreparedStatement st, int index, Object value, String typeName) throws SQLException {
        switch (typeName) {
            case TYPE_STRING :
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, (String)value);
                }
                break;
            case TYPE_BOOL :
            case TYPE_BOOLEAN :
                if (value == null) {
                    st.setNull(index, Types.BOOLEAN);
                } else {
                    st.setBoolean(index, (Boolean)value);
                }
                break;
            case TYPE_INTEGER :
            case TYPE_INT :
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setInt(index, (Integer)value);
                }
                break;
            case TYPE_LONG :
            case TYPE_LONG_PRIM :
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, (Long)value);
                }
                break;
            case TYPE_DATE :
                if (value == null) {
                    st.setNull(index, Types.INTEGER);
                } else {
                    st.setLong(index, ((Date)value).getTime());
                }
                break;
            case TYPE_BIG_DECIMAL :
                if (value == null) {
                    st.setNull(index, Types.DECIMAL);
                } else {
                    st.setBigDecimal(index, (BigDecimal)value);
                }
                break;
            case TYPE_ENUM:
                if (value == null) {
                    st.setNull(index, Types.VARCHAR);
                } else {
                    st.setString(index, ((Enum)value).name());
                }
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE);
        }
    }

    default String buildForeignKey(Field field, ForeignKey key) {
        Objects.requireNonNull(key);

        Class<?> parentTableClass = key.table();
        if (!parentTableClass.isAnnotationPresent(Table.class)) {
            throw new IllegalStateException("Foreign key references not annotated table");
        }

        String parentTableName = parentTableClass.getAnnotation(Table.class).value();
        String parentFieldName = key.field();

        StringBuilder fk = new StringBuilder();

        fk.append("FOREIGN KEY (")
                .append(field.value())
                .append(") ")
                .append("REFERENCES ")
                .append(parentTableName)
                .append("(")
                .append(parentFieldName)
                .append(")");

        if (key.onUpdate() != ReferenceOption.NONE) {
            fk.append(" ON UPDATE ")
                    .append(key.onUpdate().toString());
        }

        if (key.onDelete() != ReferenceOption.NONE) {
            fk.append(" ON DELETE ")
                    .append(key.onDelete().toString());
        }

        return fk.toString();
    }

    default String buildIndex(Table table, Method getter) {
        Field field = getter.getAnnotation(Field.class);
        Index index = getter.getAnnotation(Index.class);

        StringBuilder b = new StringBuilder("CREATE ");
        if (index.unique()) {
            b.append("UNIQUE ");
        }

        b.append("INDEX ")
                .append(index.value())
                .append(" ON ")
                .append(table.value())
                .append(" (")
                .append(field.value())
                .append(")");

        return b.toString();
    }
}
