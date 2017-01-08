/*
 *  Copyright (c) 2016, Petr Panteleyev <petr@panteleyev.org>
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification,
 *  are permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice,
 *        this list of conditions and the following disclaimer.
 *     2. Redistributions in binary form must reproduce the above copyright notice,
 *        this list of conditions and the following disclaimer in the documentation
 *        and/or other materials provided with the distribution.
 *     3. The name of the author may not be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 *  AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.panteleyev.persistence;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.ForeignKey;
import org.panteleyev.persistence.annotations.Table;
import org.sqlite.SQLiteDataSource;

public abstract class DAO {
    public static class Builder {
        private String fileName;

        public Builder() {
        }

        public Builder file(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public DataSource build() {
            Objects.requireNonNull(fileName);

            SQLiteDataSource ds = new SQLiteDataSource();
            ds.setUrl("jdbc:sqlite:" + fileName);
            ds.setEncoding("UTF-8");

            return ds;
        }
    }

    private final DAOCache CACHE = new DAOCache();
    private final Map<Class<? extends Record>, Integer> primaryKeys = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> insertSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> updateSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> deleteSQL = new ConcurrentHashMap<>();

    // Listeners
    private final Map<Class<? extends Record>, List<TableListener>> tableListeners = new ConcurrentHashMap<>();

    private DataSource datasource;

    private boolean useCache = true;

    protected DAO() {
    }

    protected DAO(DataSource ds) {
        this.datasource = ds;
    }

    protected DataSource getDataSource() {
        return datasource;
    }

    protected void setDatasource(DataSource ds) {
        this.datasource = ds;
        primaryKeys.clear();
        insertSQL.clear();
        deleteSQL.clear();
        tableListeners.clear();
    }

    public void setUseCache(boolean useCache) {
        this.useCache = useCache;
    }

    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    public <T extends Record> T get(Integer id, Class<? extends T> clazz) {
        Object cached = CACHE.get(clazz, id);
        if (cached != null) {
            return (T)cached;
        }

        try (Connection conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException("Table class is not annotated");
            }
            Table ann = clazz.getAnnotation(Table.class);

            String tableName = ann.name();
            String idName = Field.ID;

            for (Method method : clazz.getMethods()) {
                Field fieldAnn = method.getAnnotation(Field.class);
                if (fieldAnn != null && fieldAnn.primaryKey()) {
                    idName = fieldAnn.name();
                    break;
                }
            }

            String sql = "SELECT * FROM " + tableName + " WHERE " + idName + "=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet set = ps.executeQuery();
            if (set.next()) {
                T record = clazz.newInstance();
                fromSQL(set, record);
                if (useCache) {
                    if (ann.preload()) {
                        CACHE.put(record);
                    } else {
                        CACHE.putSoft(record);
                    }
                }
                return record;
            } else {
                return null;
            }
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new IllegalStateException("Unable to create table class");
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public <T extends Record> List<T> getAll(Class<T> clazz) {
        List<T> result = new ArrayList<>();

        // Check if table is preloaded
        Table t = clazz.getAnnotation(Table.class);
        if (useCache && t != null && t.preload()) {
            CACHE.stream(clazz)
               .forEach(x -> result.add((T)x));
        } else {
            try (Connection conn = getDataSource().getConnection()) {
                if (!clazz.isAnnotationPresent(Table.class)) {
                    throw new IllegalStateException("Table class is not annotated");
                }

                String tableName = clazz.getAnnotation(Table.class).name();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName);
                ResultSet set = ps.executeQuery();
                while (set.next()) {
                    T record = clazz.newInstance();
                    fromSQL(set, record);
                    if (useCache) {
                        CACHE.putSoft(record);
                    }
                    result.add(record);
                }
            } catch (InstantiationException | IllegalAccessException | SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
        return result;
    }

    protected <T extends Record> void preloadAll(Class<T> clazz) {
        try (Connection conn = getDataSource().getConnection()) {
            String tableName = clazz.getAnnotation(Table.class).name();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName);
            ResultSet set = ps.executeQuery();
            while (set.next()) {
                T record = clazz.newInstance();
                fromSQL(set, record);
                if (useCache) {
                    CACHE.put(record);
                }
            }
        } catch (InstantiationException | IllegalAccessException | SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void fromSQL(ResultSet set, Record record) throws SQLException {
        try {
            BeanInfo bi = Introspector.getBeanInfo(record.getClass());
            PropertyDescriptor[] pds = bi.getPropertyDescriptors();
            for (PropertyDescriptor pd : pds) {
                Method getter = pd.getReadMethod();
                Method setter = pd.getWriteMethod();
                if (getter != null && setter != null) {
                    Field fld = getter.getAnnotation(Field.class);
                    if (fld != null) {
                        String fName = fld.name();
                        String typeName = pd.getPropertyType().getName();

                        Object value = set.getObject(fName);
                        if (value != null) {
                            switch (typeName) {
                                case "java.lang.String" :
                                case "java.lang.Integer" :
                                case "int" :
                                    // do nothing
                                    break;
                                case "boolean" :
                                case "java.lang.Boolean" :
                                    value = (Boolean)((int)value != 0);
                                    break;
                                case "java.lang.Long" :
                                case "long" :
                                    value = (Long)value;
                                    break;
                                case "java.math.BigDecimal" :
                                    value = set.getBigDecimal(fName);
                                    break;
                                case "java.util.Date" :
                                    value = new Date(set.getLong(fName));
                                    break;
                                default:
                                    throw new IllegalStateException("Unsupported field type");
                            }
                        }

                        setter.invoke(record, value);
                    }
                }
            }
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void putIntoCache(Record item) {
        if (useCache) {
            if (((Table)item.getClass().getAnnotation(Table.class)).preload()) {
                CACHE.put(item);
            } else {
                CACHE.putSoft(item);
            }
        }
    }

    /**
     * This method creates table for the specified classes according to their annotations.
     * @param tables list of tables
     */
    public void createTables(List<Class<? extends Record>> tables) {
        if (getDataSource() == null) {
            throw new IllegalStateException("Database not opened");
        }

        try (Connection conn = getDataSource().getConnection(); Statement st = conn.createStatement()) {
            // Step 1: drop tables in reverse order
            for (int index = tables.size() - 1; index >= 0; index--) {
                Class<?> cl = tables.get(index);
                if (!cl.isAnnotationPresent(Table.class)) {
                    throw new IllegalStateException("Table class is not annotated");
                }

                Table table = cl.getAnnotation(Table.class);
                st.executeUpdate("DROP TABLE IF EXISTS " + table.name());
            }

            // Step 2: create new tables in natural order
            for (Class<?> cl : tables) {
                Table table = cl.getAnnotation(Table.class);

                try {
                    StringBuilder b = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                            .append(table.name())
                            .append(" (");

                    BeanInfo bi = Introspector.getBeanInfo(cl);
                    PropertyDescriptor[] pds = bi.getPropertyDescriptors();

                    boolean first = true;
                    for (PropertyDescriptor pd : pds) {
                        Method getter = pd.getReadMethod();
                        if (getter != null && getter.isAnnotationPresent(Field.class)) {
                            Field fld = getter.getAnnotation(Field.class);
                            String fName = fld.name();
                            String typeName = pd.getPropertyType().getName();

                            if (!first) {
                                b.append(",");
                            }
                            first = false;

                            b.append(fName).append(" ");

                            switch (typeName) {
                                case "java.lang.String" :
                                    b.append("VARCHAR(")
                                        .append(fld.length())
                                        .append(")");
                                    break;
                                case "boolean" :
                                case "java.lang.Boolean" :
                                    b.append("BOOLEAN");
                                    break;
                                case "java.lang.Integer" :
                                case "int" :
                                case "java.lang.Long" :
                                case "long" :
                                case "java.util.Date" :
                                    b.append("INTEGER");
                                    break;
                                case "java.math.BigDecimal" :
                                    b.append("VARCHAR(")
                                        .append(fld.precision() + 1)
                                        .append(")");
                                    break;
                                default:
                                    throw new IllegalStateException("Unsupported field type");
                            }

                            if (fld.primaryKey()) {
                                b.append(" PRIMARY KEY");
                            }

                            if (!fld.nullable()) {
                                b.append(" NOT NULL");
                            }

                            if (getter.isAnnotationPresent(ForeignKey.class)) {
                                ForeignKey foreignKey = getter.getAnnotation(ForeignKey.class);
                                Class<?> parentTableClass = foreignKey.table();
                                if (!parentTableClass.isAnnotationPresent(Table.class)) {
                                    throw new IllegalStateException("Foreign key references not annotated table");
                                }

                                String parentTableName = parentTableClass.getAnnotation(Table.class).name();
                                String parentFieldName = foreignKey.field();

                                b.append(" ")
//                                        .append(fName)
                                        .append("REFERENCES ")
                                        .append(parentTableName)
                                        .append("(")
                                        .append(parentFieldName)
                                        .append(")")
                                        .append(" ON UPDATE ")
                                        .append(foreignKey.onUpdate().toString())
                                        .append(" ON DELETE ")
                                        .append(foreignKey.onDelete().toString());
                            }
                        }
                    }

                    b.append(")");

                    st.executeUpdate(b.toString());
                } catch (IntrospectionException ex) {
                    throw new RuntimeException(ex);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String getInsertSQL(Record record) {
        return insertSQL.computeIfAbsent(record.getClass(), clazz -> {
            StringBuilder b = new StringBuilder("INSERT INTO ");

            Table table = (Table) clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException("Class " + clazz.getName() + " is not properly annotated");
            }

            b.append(table.name()).append(" (");

            int fCount = 0;

            try {
                BeanInfo bi = Introspector.getBeanInfo(record.getClass());
                PropertyDescriptor[] pds = bi.getPropertyDescriptors();
                for (PropertyDescriptor pd : pds) {
                    Method getter = pd.getReadMethod();
                    if (getter != null) {
                        Field fld = getter.getAnnotation(Field.class);
                        if (fld != null) {
                            if (fCount != 0) {
                                b.append(",");
                            }
                            b.append(fld.name());
                            fCount++;
                        }
                    }
                }
            } catch (IntrospectionException ex) {
                throw new RuntimeException(ex);
            }

            if (fCount == 0) {
                throw new IllegalStateException("No fields");
            }

            b.append(") VALUES (");

            while (fCount != 0) {
                b.append("?");
                if (fCount != 1) {
                    b.append(",");
                }
                fCount--;
            }

            b.append(")");
            return b.toString();
        });
    }

    private String getUpdateSQL(Record record) {
        return updateSQL.computeIfAbsent(record.getClass(), clazz -> {
            StringBuilder b = new StringBuilder("update ");

            Table table = (Table) clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException("Class is not properly annotated");
            }

            b.append(table.name()).append(" set ");

            int fCount = 0;

            try {
                BeanInfo bi = Introspector.getBeanInfo(record.getClass());
                PropertyDescriptor[] pds = bi.getPropertyDescriptors();
                for (PropertyDescriptor pd : pds) {
                    Method getter = pd.getReadMethod();
                    if (getter != null) {
                        Field fld = getter.getAnnotation(Field.class);
                        if (fld != null && !fld.primaryKey()) {
                            if (fCount != 0) {
                                b.append(", ");
                            }
                            b.append(fld.name())
                                    .append("=?");
                            fCount++;
                        }
                    }
                }
            } catch (IntrospectionException ex) {
                throw new RuntimeException(ex);
            }

            if (fCount == 0) {
                throw new IllegalStateException("No fields");
            }

            b.append(" WHERE id=?");

            return b.toString();
        });
    }

    private String getDeleteSQL(Class<? extends Record> clazz) {
        return deleteSQL.computeIfAbsent(clazz, cl -> {
            StringBuilder b = new StringBuilder("delete from ");
            Table table = (Table) cl.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException("Class is not properly annotated");
            }
            b.append(table.name())
                .append(" where ");

            String idName = null;

            try {
                BeanInfo bi = Introspector.getBeanInfo(cl);
                PropertyDescriptor[] pds = bi.getPropertyDescriptors();
                for (PropertyDescriptor pd : pds) {
                    Method getter = pd.getReadMethod();
                    if (getter != null) {
                        Field fld = getter.getAnnotation(Field.class);
                        if (fld != null && fld.primaryKey()) {
                            idName = fld.name();
                            break;
                        }
                    }
                }
            } catch (IntrospectionException ex) {
                throw new RuntimeException(ex);
            }

            if (idName == null) {
                throw new IllegalStateException("Class is not properly annotated");
            }

            b.append(idName)
                .append("=?");

            return b.toString();
        });
    }

    private String getDeleteSQL(Record record) {
        return getDeleteSQL(record.getClass());
    }

    private void setData(Record record, PreparedStatement st, boolean update) throws SQLException {
        try {
            BeanInfo bi = Introspector.getBeanInfo(record.getClass());
            PropertyDescriptor[] pds = bi.getPropertyDescriptors();

            int index = 1;
            for (PropertyDescriptor pd : pds) {
                Method getter = pd.getReadMethod();
                if (getter != null && getter.isAnnotationPresent(Field.class)) {
                    // if update skip ID at this point
                    Field fld = getter.getAnnotation(Field.class);
                    if (update && fld.primaryKey()) {
                        continue;
                    }

                    String typeName = pd.getPropertyType().getName();

                    Object value = getter.invoke(record);

                    switch (typeName) {
                        case "java.lang.String" :
                            if (value == null) {
                                st.setNull(index++, Types.VARCHAR);
                            } else {
                                st.setString(index++, (String)value);
                            }
                            break;
                        case "boolean" :
                        case "java.lang.Boolean" :
                            if (value == null) {
                                st.setNull(index++, Types.BOOLEAN);
                            } else {
                                st.setBoolean(index++, (Boolean)value);
                            }
                            break;
                        case "java.lang.Integer" :
                        case "int" :
                            if (value == null) {
                                st.setNull(index++, Types.INTEGER);
                            } else {
                                st.setInt(index++, (Integer)value);
                            }
                            break;
                        case "java.lang.Long" :
                        case "long" :
                            if (value == null) {
                                st.setNull(index++, Types.INTEGER);
                            } else {
                                st.setLong(index++, (Long)value);
                            }
                            break;
                        case "java.util.Date" :
                            if (value == null) {
                                st.setNull(index++, Types.INTEGER);
                            } else {
                                st.setLong(index++, ((Date)value).getTime());
                            }
                            break;
                        case "java.math.BigDecimal" :
                            if (value == null) {
                                st.setNull(index++, Types.DECIMAL);
                            } else {
                                st.setBigDecimal(index++, (BigDecimal)value);
                            }
                            break;
                        default:
                            throw new IllegalStateException("Unsupported data type");
                    }
                }
            }

            if (update) {
                st.setInt(index, record.getId());
            }
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected PreparedStatement getInsertStatement(Record record, Connection conn) throws SQLException {
        String sql = getInsertSQL(record);
        PreparedStatement st = conn.prepareStatement(sql);
        setData(record, st, false);
        return st;
    }

    public PreparedStatement getPreparedStatement(Record record, Connection conn) throws SQLException {
        boolean update = true;
        if (record.getId() == null) {
            record.setId(generatePrimaryKey(record.getClass()));
            update = false;
        }

        String sql = (update)? getUpdateSQL(record) : getInsertSQL(record);
        PreparedStatement st = conn.prepareStatement(sql);
        setData(record, st, update);
        return st;
    }

    private PreparedStatement getDeleteStatement(Record record, Connection conn) throws SQLException {
        PreparedStatement st = conn.prepareStatement(getDeleteSQL(record));
        st.setInt(1, record.getId());
        return st;
    }

    private PreparedStatement getDeleteStatement(Integer id, Class<? extends Record> clazz, Connection conn) throws SQLException {
        PreparedStatement st = conn.prepareStatement(getDeleteSQL(clazz));
        st.setInt(1, id);
        return st;
    }

    public void preload(List<Class<? extends Record>> tables) {
        tables.stream()
            .filter(x -> {
                if (x.isAnnotationPresent(Table.class)) {
                    Table a = x.getAnnotation(Table.class);
                    return a.preload();
                } else {
                    return false;
                }
            }).forEach(x -> {
                preloadAll(x);
            });

        // load primary key max values
        tables.stream()
            .filter(x -> {
                return x.isAnnotationPresent(Table.class);
            }).forEach(x -> {
                Table a = x.getAnnotation(Table.class);
                Integer id = getIdMaxValue(a.name());
                primaryKeys.put(x, id);
            });
    }

    public Integer generatePrimaryKey(Class<? extends Record> clazz) {
        return primaryKeys.compute(clazz, (k, v) -> (v == null)? 1 : ++v);
    }

    private Integer getIdMaxValue(String tableName) {
        try (Connection conn = getDataSource().getConnection()) {
            PreparedStatement st = conn.prepareStatement("SELECT id FROM " + tableName + " ORDER BY id DESC");
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                return 0;
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method inserts new record with predefined id into the database. No attempt to generate
     * new id is made. Calling code must be sure that predefined id is already unique.
     * @param <T> type of the record
     * @param record record
     * @return record id
     */
    public <T extends Record> Integer insert(T record) {
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getInsertStatement(record, conn)) {
            ps.executeUpdate();
            putIntoCache(record);
            return record.getId();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public <T extends Record> Integer put(T record) {
        boolean update = record.getId() != null;

        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getPreparedStatement(record, conn)) {
            ps.executeUpdate();
            putIntoCache(record);

            // Call listeners
            getListeners(record.getClass()).forEach(l -> {
                if (update) {
                    l.recordUpdated(record);
                } else {
                    l.recordAdded(record);
                }
            });

            return record.getId();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void delete(Record record) {
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getDeleteStatement(record, conn)) {
            ps.executeUpdate();
            CACHE.remove(record);
            getListeners(record.getClass()).forEach(l -> l.recordDeleted(record));
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void delete(Integer id, Class<? extends Record> clazz) {
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getDeleteStatement(id, clazz, conn)) {
            ps.executeUpdate();
            CACHE.remove(id, clazz);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void addListener(Class<? extends Record> clazz, TableListener listener) {
        getListeners(clazz).add(listener);
    }

    public void removeListener(Class<? extends Record> clazz, TableListener listener) {
        getListeners(clazz).remove(listener);
    }

    private List<TableListener> getListeners(Class<? extends Record> clazz) {
        return tableListeners.computeIfAbsent(clazz, cl -> new ArrayList<>());
    }
}
