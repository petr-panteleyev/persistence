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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

class SQLiteProxy implements DAOProxy, DAOTypes {

    @Override
    public Object getFieldValue(String fieldName, Class typeClass, ResultSet set) throws SQLException {
        Object value = set.getObject(fieldName);

        String typeName = typeClass.isEnum() ? TYPE_ENUM : typeClass.getName();

        if (value != null) {
            switch (typeName) {
                case TYPE_STRING:
                case TYPE_INTEGER:
                case TYPE_INT:
                case TYPE_LONG:
                case TYPE_LONG_PRIM:
                    // do nothing
                    break;
                case TYPE_BOOL:
                case TYPE_BOOLEAN:
                    value = (int) value != 0;
                    break;
                case TYPE_BIG_DECIMAL:
                    value = set.getBigDecimal(fieldName);
                    break;
                case TYPE_DATE:
                    value = new Date(set.getLong(fieldName));
                    break;
                case TYPE_LOCAL_DATE:
                    value = LocalDate.ofEpochDay(set.getLong(fieldName));
                    break;
                case TYPE_ENUM:
                    value = Enum.valueOf(typeClass, (String) value);
                    break;
                default:
                    throw new IllegalStateException(BAD_FIELD_TYPE);
            }
        } else {
            switch (typeName) {
                case TYPE_INT:
                    value = 0;
                    break;
                case TYPE_LONG_PRIM:
                    value = 0L;
                    break;
                case TYPE_BOOL:
                    value = false;
                    break;
            }
        }

        return value;
    }

    public String getColumnString(Field fld, ForeignKey foreignKey, String typeName, List<String> constraints) {
        StringBuilder b = new StringBuilder();

        switch (typeName) {
            case TYPE_STRING:
            case TYPE_ENUM:
                b.append("VARCHAR(")
                        .append(fld.length())
                        .append(")");
                break;
            case TYPE_BOOL:
            case TYPE_BOOLEAN:
                b.append("BOOLEAN");
                break;
            case TYPE_INTEGER:
            case TYPE_INT:
            case TYPE_LONG:
            case TYPE_LONG_PRIM:
            case TYPE_DATE:
            case TYPE_LOCAL_DATE:
                b.append("INTEGER");
                break;
            case TYPE_BIG_DECIMAL:
                b.append("VARCHAR(")
                        .append(fld.precision() + 1)
                        .append(")");
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE);
        }

        if (fld.primaryKey()) {
            b.append(" PRIMARY KEY");
        }

        if (!fld.nullable()) {
            b.append(" NOT NULL");
        }

        if (foreignKey != null) {
            constraints.add(buildForeignKey(fld, foreignKey));
        }

        return b.toString();
    }

    public void truncate(Connection connection, List<Class<? extends Record>> tables) {
        tables.forEach(table -> {
            deleteAll(connection, table);
        });

        try (Statement statement = connection.createStatement()) {
            statement.execute("VACUUM");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
