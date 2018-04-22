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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

class SQLiteProxy implements DAOProxy, DAOTypes {
    private static final Map<String, BiFunction<ResultSet, String, ?>> RESULT_SET_READERS = Map.ofEntries(
            Map.entry(TYPE_STRING, OBJECT_READER),
            Map.entry(TYPE_INTEGER, OBJECT_READER),
            Map.entry(TYPE_INT, INT_READER),
            Map.entry(TYPE_LONG, OBJECT_READER),
            Map.entry(TYPE_LONG_PRIM, LONG_READER),
            Map.entry(TYPE_BOOL, INT_BOOL_READER),
            Map.entry(TYPE_BOOLEAN, INT_BOOLEAN_READER),
            Map.entry(TYPE_BIG_DECIMAL, BIG_DECIMAL_READER),
            Map.entry(TYPE_DATE, DATE_READER),
            Map.entry(TYPE_LOCAL_DATE, LOCAL_DATE_READER)
    );

    @Override
    public Map<String, BiFunction<ResultSet, String, ?>> getReaderMap() {
        return RESULT_SET_READERS;
    }

    public String getColumnString(Column column, ForeignKey foreignKey, String typeName, List<String> constraints) {
        var b = new StringBuilder();

        switch (typeName) {
            case TYPE_STRING:
            case TYPE_ENUM:
                b.append("VARCHAR(")
                        .append(column.length())
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
                        .append(column.precision() + 1)
                        .append(")");
                break;
            default:
                throw new IllegalStateException(BAD_FIELD_TYPE);
        }

        if (column.primaryKey()) {
            b.append(" PRIMARY KEY");
        }

        if (!column.nullable()) {
            b.append(" NOT NULL");
        }

        if (foreignKey != null) {
            constraints.add(buildForeignKey(column, foreignKey));
        }

        return b.toString();
    }

    public void truncate(Connection connection, List<Class<? extends Record>> tables) {
        tables.forEach(table -> {
            deleteAll(connection, table);
        });

        try (var statement = connection.createStatement()) {
            statement.execute("VACUUM");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
