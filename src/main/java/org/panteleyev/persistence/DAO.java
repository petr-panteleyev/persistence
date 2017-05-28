/*
 * Copyright (c) 2016, 2017, Petr Panteleyev <petr@panteleyev.org>
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
import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.annotations.Table;
import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static org.panteleyev.persistence.DAOTypes.BAD_FIELD_TYPE;
import static org.panteleyev.persistence.DAOTypes.TYPE_ENUM;

/**
 * Persistence API entry point.
 */
public class DAO {
    private static final String NOT_ANNOTATED    = "Class is not properly annotated";

    private final Map<Class<? extends Record>, Integer> primaryKeys = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> insertSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> updateSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> deleteSQL = new ConcurrentHashMap<>();

    private DataSource datasource;

    private DAOProxy proxy;

    public DAO() {
    }

    public DAO(DataSource ds) {
        this.datasource = ds;
        proxy = setupProxy();
    }

    /**
     * Return current data source object.
     * @return data source object
     */
    public DataSource getDataSource() {
        return datasource;
    }

    /**
     * Sets a new data source.
     * @param ds data source
     */
    public void setDataSource(DataSource ds) {
        this.datasource = ds;
        primaryKeys.clear();
        insertSQL.clear();
        deleteSQL.clear();
        proxy = setupProxy();
    }

    private DAOProxy setupProxy() {
        // TODO: figure out better way instead of class name check
        String dsClass = datasource.getClass().getName().toLowerCase();

        if (dsClass.contains("mysql")) {
            return new MySQLProxy();
        }

        if (dsClass.contains("sqlite")) {
            return new SQLiteProxy();
        }

        throw new IllegalStateException("Unsupported database type");
    }

    /**
     * Returns connection for the current data source.
     * @return connection
     * @throws SQLException in case of SQL error
     */
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Retrieves record from the database using record ID.
     * @param id record id
     * @param clazz record class
     * @param <T> type of the record
     * @return record
     */
    public <T extends Record> T get(Integer id, Class<? extends T> clazz) {
        try (Connection conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }
            Table ann = clazz.getAnnotation(Table.class);

            String tableName = ann.value();
            String idName = Field.ID;

            for (Method method : clazz.getMethods()) {
                Field fieldAnn = method.getAnnotation(Field.class);
                if (fieldAnn != null && fieldAnn.primaryKey()) {
                    idName = fieldAnn.value();
                    break;
                }
            }

            String sql = "SELECT * FROM " + tableName + " WHERE " + idName + "=?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, id);
            ResultSet set = ps.executeQuery();

            return (set.next())? fromSQL(set, clazz) : null;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Retrieves all records of the specified type.
     * @param clazz record class
     * @param <T> type of the record
     * @return list of records
     */
    public <T extends Record> List<T> getAll(Class<T> clazz) {
        List<T> result = new ArrayList<>();

        try (Connection conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            String tableName = clazz.getAnnotation(Table.class).value();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName);
            ResultSet set = ps.executeQuery();
            while (set.next()) {
                result.add(fromSQL(set, clazz));
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    /**
     * Retrieves all records of the specified type and fills the map.
     * @param clazz record class
     * @param <T> type of the record
     * @param result map to fill
     */
    public <T extends Record> void getAll(Class<T> clazz, Map<Integer, T> result) {
        try (Connection conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            String tableName = clazz.getAnnotation(Table.class).value();
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + tableName);
            ResultSet set = ps.executeQuery();
            while (set.next()) {
                T r = fromSQL(set, clazz);
                result.put(r.getId(), r);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private <T extends Record> T fromSQL(ResultSet set, Class<T> clazz) {
        try {
            // First try to find @RecordBuilder constructor
            for (Constructor constructor : clazz.getConstructors()) {
                if (constructor.isAnnotationPresent(RecordBuilder.class)) {
                    return fromSQL(set, constructor);
                }
            }

            T result = clazz.newInstance();
            fromSQL(set, result);
            return result;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends Record> T fromSQL(ResultSet set, Constructor<?> constructor) throws SQLException, IllegalAccessException, InvocationTargetException, InstantiationException {
        int paramCount = constructor.getParameterCount();

        Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramCount];

        for (int i = 0; i < paramCount; i++) {
            String fieldName = Arrays.stream(paramAnnotations[i])
                    .filter(a -> a instanceof Field)
                    .findAny()
                    .map(a -> ((Field)a).value())
                    .orElseThrow(RuntimeException::new);

            params[i] = proxy.getFieldValue(fieldName, paramTypes[i], set);
        }

        return (T)constructor.newInstance(params);
    }

    private void fromSQL(ResultSet set, Record record) throws SQLException {
        try {
            BeanInfo bi = Introspector.getBeanInfo(record.getClass());
            PropertyDescriptor[] pds = bi.getPropertyDescriptors();
            for (PropertyDescriptor pd : pds) {
                Method getter = pd.getReadMethod();
                Method setter = pd.getWriteMethod();

                Class getterClass = getter.getReturnType();
                if (getterClass.equals(Optional.class)) {
                    getterClass = getEffectiveType(getter);

                    String setterName = getter.getName().replace("get", "set");
                    for (Method m : record.getClass().getDeclaredMethods()) {
                        if (m.getParameterCount() == 1 && m.getName().equals(setterName)) {
                            setter = m;
                            break;
                        }
                    }
                }

                if (setter != null) {
                    Field fld = getter.getAnnotation(Field.class);
                    if (fld != null) {
                        setter.invoke(record, proxy.getFieldValue(fld.value(), getterClass, set));
                    }
                }
            }
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Class getEffectiveType(Method getter) {
        Type rType = getter.getGenericReturnType();

        if (rType instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType)rType).getActualTypeArguments();
            if (actualTypeArguments.length != 1) {
                throw new IllegalStateException(BAD_FIELD_TYPE);
            } else {
                return (Class)actualTypeArguments[0];
            }
        } else {
            return (Class)rType;
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
                    throw new IllegalStateException(NOT_ANNOTATED);
                }

                Table table = cl.getAnnotation(Table.class);
                st.executeUpdate("DROP TABLE IF EXISTS " + table.value());
            }

            // Step 2: create new tables in natural order
            for (Class<?> cl : tables) {
                Table table = cl.getAnnotation(Table.class);

                try {
                    StringBuilder b = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                            .append(table.value())
                            .append(" (");

                    BeanInfo bi = Introspector.getBeanInfo(cl);
                    PropertyDescriptor[] pds = bi.getPropertyDescriptors();

                    List<String> constraints = new ArrayList<>();

                    Set<Method> indexed = new HashSet<>();

                    boolean first = true;
                    for (PropertyDescriptor pd : pds) {
                        Method getter = pd.getReadMethod();
                        if (getter != null && getter.isAnnotationPresent(Field.class)) {
                            Field fld = getter.getAnnotation(Field.class);
                            String fName = fld.value();

                            Class getterType = getEffectiveType(getter);
                            String typeName = getterType.isEnum() ?
                                    TYPE_ENUM : getterType.getTypeName();

                            if (!first) {
                                b.append(",");
                            }
                            first = false;

                            b.append(fName)
                                    .append(" ")
                                    .append(proxy.getColumnString(fld,
                                            getter.getAnnotation(ForeignKey.class), typeName, constraints));

                            if (getter.isAnnotationPresent(Index.class)) {
                                indexed.add(getter);
                            }
                        }
                    }

                    if (!constraints.isEmpty()) {
                        b.append(",");
                        b.append(constraints.stream().collect(Collectors.joining(",")));
                    }

                    b.append(")");

                    st.executeUpdate(b.toString());

                    // Create indexes
                    for (Method getter : indexed) {
                        st.executeUpdate(proxy.buildIndex(table, getter));
                    }

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

            Table table = clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException("Class " + clazz.getName() + " is not properly annotated");
            }

            b.append(table.value()).append(" (");

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
                            b.append(fld.value());
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

            Table table = clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            b.append(table.value()).append(" set ");

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
                            b.append(fld.value())
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
            Table table = cl.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }
            b.append(table.value())
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
                            idName = fld.value();
                            break;
                        }
                    }
                }
            } catch (IntrospectionException ex) {
                throw new RuntimeException(ex);
            }

            if (idName == null) {
                throw new IllegalStateException(NOT_ANNOTATED);
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

                    Object value = getter.invoke(record);

                    Class getterClass = getter.getReturnType();

                    if (getterClass.equals(Optional.class)) {
                        getterClass = getEffectiveType(getter);

                        Method isPresentMethod = Optional.class.getDeclaredMethod("isPresent");
                        if ((Boolean)isPresentMethod.invoke(value)) {
                            value = Optional.class
                                    .getDeclaredMethod("get")
                                    .invoke(value);
                        } else {
                            value = null;
                        }
                    }

                    String typeName = getterClass.isEnum()? TYPE_ENUM : getterClass.getName();
                    proxy.setFieldData(st, index++, value, typeName);
                }
            }

            if (update) {
                st.setInt(index, record.getId());
            }
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private PreparedStatement getPreparedStatement(Record record, Connection conn, boolean update) throws SQLException {
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

    /**
     * Pre-loads necessary information from the just opened database. This method must be called prior to any other
     * database operations. Otherwise primary keys may be generated incorrectly.
     * @param tables list of {@link Record} types
     */
    public void preload(Collection<Class<? extends Record>> tables) {
        // load primary key max values
        tables.stream()
                .filter(x -> x.isAnnotationPresent(Table.class))
                .forEach(x -> {
                    Table a = x.getAnnotation(Table.class);
                    Integer id = getIdMaxValue(a.value());
                    primaryKeys.put(x, id);
                });
    }

    /**
     * Returns next available primary key value. This method is thread safe.
     * @param clazz record class
     * @return primary key value
     */
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
     * @return inserted record
     */
    public <T extends Record> T insert(T record) {
        Objects.requireNonNull(record.getId());

        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = getPreparedStatement(record, conn, false)) {
            ps.executeUpdate();

            return get(record.getId(), (Class<T>)record.getClass());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Updates record in the database. This method returns instance of the {@link Record}, i.e. supplied object is
     * not changed.
     * @param record record
     * @param <T> record type
     * @return updated record
     */
    public <T extends Record> T update(T record) {
        Objects.requireNonNull(record.getId());

        try (Connection conn = getDataSource().getConnection();
                PreparedStatement ps = getPreparedStatement(record, conn, true)) {
            ps.executeUpdate();

            return get(record.getId(), (Class<T>)record.getClass());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deleted record from the database.
     * @param record record to delete
     */
    public void delete(Record record) {
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getDeleteStatement(record, conn)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes record from the database.
     * @param id id of the record
     * @param clazz record type
     */
    public void delete(Integer id, Class<? extends Record> clazz) {
        try (Connection conn = getDataSource().getConnection(); PreparedStatement ps = getDeleteStatement(id, clazz, conn)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
