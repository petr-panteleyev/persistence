/*
 * Copyright (c) 2016, 2018, Petr Panteleyev <petr@panteleyev.org>
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
import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.annotations.Table;
import javax.sql.DataSource;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static org.panteleyev.persistence.DAOTypes.TYPE_ENUM;

/**
 * Persistence API entry point.
 */
public class DAO {
    static class ParameterHandle {
        final String name;
        final Class<?> type;

        ParameterHandle(String name, Class<?> type) {
            this.name = name;
            this.type = type;
        }
    }

    static class ConstructorHandle {
        final MethodHandle handle;
        final List<ParameterHandle> parameters;

        ConstructorHandle(MethodHandle handle, List<ParameterHandle> parameters) {
            this.handle = handle;
            this.parameters = parameters;
        }
    }

    private static final String NOT_ANNOTATED = "Class is not properly annotated";

    private final Map<Class<? extends Record>, Integer> primaryKeys = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> insertSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> updateSQL = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, String> deleteSQL = new ConcurrentHashMap<>();

    private final Map<Class<? extends Record>, ConstructorHandle> constructorMap = new ConcurrentHashMap<>();
    private final Map<Class<? extends Record>, Map<String, VarHandle>> columnMap = new ConcurrentHashMap<>();

    private DataSource datasource;

    private DAOProxy proxy;

    public DAO() {
    }

    // Test only
    DAO(DAOProxy proxy) {
        this.proxy = proxy;
    }

    public DAO(DataSource ds) {
        this.datasource = ds;
        proxy = setupProxy();
    }

    /**
     * Return current data source object.
     *
     * @return data source object
     */
    public DataSource getDataSource() {
        return datasource;
    }

    /**
     * Sets a new data source.
     *
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
        if (datasource != null) {
            var dsClass = datasource.getClass().getName().toLowerCase();

            if (dsClass.contains("mysql")) {
                return new MySQLProxy();
            }

            if (dsClass.contains("sqlite")) {
                return new SQLiteProxy();
            }

            throw new IllegalStateException("Unsupported database type");
        } else {
            return null;
        }
    }

    /**
     * Returns connection for the current data source.
     *
     * @return connection
     * @throws SQLException in case of SQL error
     */
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Retrieves record from the database using record ID.
     *
     * @param id    record id
     * @param clazz record class
     * @param <T>   type of the record
     * @return record
     */
    public <T extends Record> T get(Integer id, Class<? extends T> clazz) {
        try (var conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }
            var ann = clazz.getAnnotation(Table.class);

            var tableName = ann.value();
            var idName = Column.ID;

            for (var field : clazz.getDeclaredFields()) {
                var fieldAnn = field.getAnnotation(Column.class);
                if (fieldAnn != null && fieldAnn.primaryKey()) {
                    idName = fieldAnn.value();
                    break;
                }
            }

            var sql = "SELECT * FROM " + tableName + " WHERE " + idName + "=?";
            var ps = conn.prepareStatement(sql);
            ps.setInt(1, id);

            try (var set = ps.executeQuery()) {
                return (set.next()) ? fromSQL(set, clazz) : null;
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Retrieves all records of the specified type.
     *
     * @param clazz record class
     * @param <T>   type of the record
     * @return list of records
     */
    public <T extends Record> List<T> getAll(Class<T> clazz) {
        var result = new ArrayList<T>();

        try (var conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            var tableName = clazz.getAnnotation(Table.class).value();
            var ps = conn.prepareStatement("SELECT * FROM " + tableName);
            try (var set = ps.executeQuery()) {
                while (set.next()) {
                    result.add(fromSQL(set, clazz));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    /**
     * Retrieves all records of the specified type and fills the map.
     *
     * @param clazz  record class
     * @param <T>    type of the record
     * @param result map to fill
     */
    public <T extends Record> void getAll(Class<T> clazz, Map<Integer, T> result) {
        try (var conn = getDataSource().getConnection()) {
            if (!clazz.isAnnotationPresent(Table.class)) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            var tableName = clazz.getAnnotation(Table.class).value();
            var ps = conn.prepareStatement("SELECT * FROM " + tableName);
            try (var set = ps.executeQuery()) {
                while (set.next()) {
                    T r = fromSQL(set, clazz);
                    result.put(r.getId(), r);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    static Map<String, VarHandle> computeColumns(Class<? extends Record> clazz) {
        try {
            var lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());

            var result = new HashMap<String, VarHandle>();
            for (var field : clazz.getDeclaredFields()) {
                var column = field.getAnnotation(Column.class);
                if (column != null) {
                    var handle = lookup.unreflectVarHandle(field);
                    result.put(column.value(), handle);
                }
            }
            return result;
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }


    private void fromSQL(ResultSet set, Record record, Map<String, VarHandle> columns) throws SQLException {
        for (var entry : columns.entrySet()) {
            var handle = entry.getValue();
            var value = proxy.getFieldValue(entry.getKey(), handle.varType(), set);

            switch (handle.varType().getName()) {
                case "int":
                    handle.set(record, value == null ? 0 : (int) value);
                    break;
                case "long":
                    handle.set(record, value == null ? 0L : (long) value);
                    break;
                case "boolean":
                    handle.set(record, value != null && (boolean) value);
                    break;
                default:
                    handle.set(record, value);
                    break;
            }
        }
    }

    private <T extends Record> T fromSQL(ResultSet set, ConstructorHandle builder) {
        try {
            var params = new ArrayList<>(builder.parameters.size());
            for (ParameterHandle ph : builder.parameters) {
                params.add(proxy.getFieldValue(ph.name, ph.type, set));
            }

            //noinspection unchecked
            return (T) builder.handle.invokeWithArguments(params);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    <T extends Record> T fromSQL(ResultSet set, Class<T> clazz) {
        var builder = constructorMap.computeIfAbsent(clazz, DAO::cacheConstructorHandle);

        try {
            if (builder != null) {
                return fromSQL(set, builder);
            } else {
                var columns = columnMap.computeIfAbsent(clazz, DAO::computeColumns);
                if (columns.isEmpty()) {
                    throw new IllegalStateException("Class " + clazz.getName() + " has no column annotations");
                }

                T result = clazz.getDeclaredConstructor().newInstance();
                fromSQL(set, result, columns);
                return result;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method creates table for the specified classes according to their annotations.
     *
     * @param tables list of tables
     */
    public void createTables(List<Class<? extends Record>> tables) {
        if (getDataSource() == null) {
            throw new IllegalStateException("Database not opened");
        }

        try (var conn = getDataSource().getConnection(); var st = conn.createStatement()) {
            // Step 1: drop tables in reverse order
            for (int index = tables.size() - 1; index >= 0; index--) {
                var cl = tables.get(index);
                if (!cl.isAnnotationPresent(Table.class)) {
                    throw new IllegalStateException(NOT_ANNOTATED);
                }

                var table = cl.getAnnotation(Table.class);
                st.executeUpdate("DROP TABLE IF EXISTS " + table.value());
            }

            // Step 2: create new tables in natural order
            for (Class<?> cl : tables) {
                var table = cl.getAnnotation(Table.class);

                try {
                    var b = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                            .append(table.value())
                            .append(" (");

                    var constraints = new ArrayList<String>();
                    var indexed = new HashSet<Field>();

                    boolean first = true;
                    for (var field : cl.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Column.class)) {
                            var column = field.getAnnotation(Column.class);
                            var fName = column.value();

                            var getterType = field.getType();
                            var typeName = getterType.isEnum() ?
                                    TYPE_ENUM : getterType.getTypeName();

                            if (!first) {
                                b.append(",");
                            }
                            first = false;

                            b.append(fName).append(" ")
                                    .append(proxy.getColumnString(column,
                                            field.getAnnotation(ForeignKey.class), typeName, constraints));

                            if (field.isAnnotationPresent(Index.class)) {
                                indexed.add(field);
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
                    for (var field : indexed) {
                        st.executeUpdate(proxy.buildIndex(table, field));
                    }
                } catch (SecurityException | SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String getInsertSQL(Record record) {
        return insertSQL.computeIfAbsent(record.getClass(), clazz -> {
            var b = new StringBuilder("INSERT INTO ");

            var table = clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException("Class " + clazz.getName() + " is not properly annotated");
            }

            b.append(table.value()).append(" (");

            int fCount = 0;

            try {
                for (var field : clazz.getDeclaredFields()) {
                    var column = field.getAnnotation(Column.class);
                    if (column != null) {
                        if (fCount != 0) {
                            b.append(",");
                        }
                        b.append(column.value());
                        fCount++;
                    }
                }
            } catch (SecurityException ex) {
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
            var b = new StringBuilder("update ");

            var table = clazz.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }

            b.append(table.value()).append(" set ");

            int fCount = 0;

            try {
                for (var field : record.getClass().getDeclaredFields()) {
                    var column = field.getAnnotation(Column.class);
                    if (column != null && !column.primaryKey()) {
                        if (fCount != 0) {
                            b.append(", ");
                        }
                        b.append(column.value())
                                .append("=?");
                        fCount++;
                    }
                }
            } catch (SecurityException ex) {
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
            var b = new StringBuilder("DELETE FROM ");
            var table = cl.getAnnotation(Table.class);
            if (table == null) {
                throw new IllegalStateException(NOT_ANNOTATED);
            }
            b.append(table.value())
                    .append(" WHERE ");

            String idName = null;

            try {
                for (var field : clazz.getDeclaredFields()) {
                    var column = field.getAnnotation(Column.class);
                    if (column != null && column.primaryKey()) {
                        idName = column.value();
                        break;
                    }
                }
            } catch (SecurityException ex) {
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

    private void setData(Record record, PreparedStatement st, boolean update) {
        try {
            int index = 1;

            var columns = columnMap.computeIfAbsent(record.getClass(), DAO::computeColumns);

            for (var field : record.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    // if update skip ID at this point
                    var fld = field.getAnnotation(Column.class);
                    if (update && fld.primaryKey()) {
                        continue;
                    }

                    var handle = columns.get(fld.value());
                    var fieldType = field.getType();

                    Object value = handle.get(record);
                    var typeName = fieldType.isEnum() ? TYPE_ENUM : fieldType.getTypeName();
                    proxy.setFieldData(st, index++, value, typeName);
                }
            }

            if (update) {
                st.setInt(index, record.getId());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private PreparedStatement getPreparedStatement(Record record, Connection conn, boolean update) throws SQLException {
        String sql = (update) ? getUpdateSQL(record) : getInsertSQL(record);
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
     *
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
     *
     * @param clazz record class
     * @return primary key value
     */
    public Integer generatePrimaryKey(Class<? extends Record> clazz) {
        return primaryKeys.compute(clazz, (k, v) -> (v == null) ? 1 : ++v);
    }

    private Integer getIdMaxValue(String tableName) {
        try (var conn = getDataSource().getConnection()) {
            var st = conn.prepareStatement("SELECT id FROM " + tableName + " ORDER BY id DESC");
            try (var rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return 0;
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method inserts new record with predefined id into the database. No attempt to generate
     * new id is made. Calling code must ensure that predefined id is unique.
     *
     * @param <T>    type of the record
     * @param record record
     * @return inserted record
     * @throws IllegalArgumentException if id of the record is 0
     */
    public <T extends Record> T insert(T record) {
        try (var conn = getDataSource().getConnection()) {
            return insert(conn, record);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * This method inserts new record with predefined id into the database. No attempt to generate
     * new id is made. Calling code must ensure that predefined id is unique.
     *
     * @param <T>    type of the record
     * @param conn   SQL connection
     * @param record record
     * @return inserted record
     * @throws IllegalArgumentException if id of the record is 0
     */
    public <T extends Record> T insert(Connection conn, T record) {
        if (record.getId() == 0) {
            throw new IllegalArgumentException("id == 0");
        }

        try (var st = getPreparedStatement(record, conn, false)) {
            st.executeUpdate();
            //noinspection unchecked
            return get(record.getId(), (Class<T>) record.getClass());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * <p>This method inserts multiple records with predefined id using batch insert. No attempt to generate
     * new id is made. Calling code must ensure that predefined id is unique for all records.</p>
     * <p>Supplied records are divided to batches of the specified size. To avoid memory issues size of the batch
     * must be tuned appropriately.</p>
     *
     * @param size    size of the batch
     * @param records list of records
     * @param <T>     type of records
     */
    public <T extends Record> void insert(int size, List<T> records) {
        try (var conn = getConnection()) {
            insert(conn, size, records);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * <p>This method inserts multiple records with predefined id using batch insert. No attempt to generate
     * new id is made. Calling code must ensure that predefined id is unique for all records.</p>
     * <p>Supplied records are divided to batches of the specified size. To avoid memory issues size of the batch
     * must be tuned appropriately.</p>
     *
     * @param conn    SQL connection
     * @param size    size of the batch
     * @param records list of records
     * @param <T>     type of records
     */
    public <T extends Record> void insert(Connection conn, int size, List<T> records) {
        if (size < 1) {
            throw new IllegalArgumentException("Batch size must be >= 1");
        }

        if (!records.isEmpty()) {
            var sql = getInsertSQL(records.get(0));

            try (var st = conn.prepareStatement(sql)) {
                int count = 0;

                for (T r : records) {
                    setData(r, st, false);
                    st.addBatch();

                    if (++count % size == 0) {
                        st.executeBatch();
                    }
                }

                st.executeBatch();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Updates record in the database. This method returns instance of the {@link Record}, i.e. supplied object is
     * not changed.
     *
     * @param record record
     * @param <T>    record type
     * @return updated record
     * @throws IllegalArgumentException if id of the record is 0
     */
    public <T extends Record> T update(T record) {
        try (var conn = getDataSource().getConnection()) {
            return update(conn, record);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Updates record in the database. This method returns instance of the {@link Record}, i.e. supplied object is
     * not changed.
     *
     * @param conn   SQL connection
     * @param record record
     * @param <T>    record type
     * @return updated record
     * @throws IllegalArgumentException if id of the record is 0
     */
    public <T extends Record> T update(Connection conn, T record) {
        if (record.getId() == 0) {
            throw new IllegalArgumentException("id == 0");
        }

        try (var ps = getPreparedStatement(record, conn, true)) {
            ps.executeUpdate();
            //noinspection unchecked
            return get(record.getId(), (Class<T>) record.getClass());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * Deleted record from the database.
     *
     * @param record record to delete
     */
    public void delete(Record record) {
        try (var conn = getDataSource().getConnection(); var ps = getDeleteStatement(record, conn)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes record from the database.
     *
     * @param id    id of the record
     * @param clazz record type
     */
    public void delete(Integer id, Class<? extends Record> clazz) {
        try (var conn = getDataSource().getConnection(); var ps = getDeleteStatement(id, clazz, conn)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes all records from table.
     *
     * @param table table
     */
    public void deleteAll(Class<? extends Record> table) {
        try (var connection = getDataSource().getConnection()) {
            deleteAll(connection, table);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Deletes all records from table using provided connection.
     *
     * @param connection SQL connection
     * @param table      table class
     */
    public void deleteAll(Connection connection, Class<? extends Record> table) {
        proxy.deleteAll(connection, table);
    }

    /**
     * Truncates tables removing all records. Primary key generation starts from 1 again. For MySQL this operation
     * uses <code>TRUNCATE TABLE table_name</code> command. As SQLite does not support this command <code>DELETE FROM
     * table_name</code> is used instead.
     *
     * @param tables tables to truncate
     */
    public void truncate(List<Class<? extends Record>> tables) {
        try (var connection = getDataSource().getConnection()) {
            proxy.truncate(connection, tables);
            for (Class<? extends Record> t : tables) {
                primaryKeys.put(t, 0);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Resets primary key generation for the given table. Next call to {@link DAO#generatePrimaryKey(Class)}
     * will return 1. This method should only be used in case of manual table truncate.
     *
     * @param table table class
     */
    protected void resetPrimaryKey(Class<? extends Record> table) {
        primaryKeys.put(table, 0);
    }

    /**
     * Drops specified tables according to their annotations.
     *
     * @param tables table classes
     */
    public void dropTables(List<Class<? extends Record>> tables) {
        try (var conn = getDataSource().getConnection(); var st = conn.createStatement()) {
            for (Class<? extends Record> t : tables) {
                st.execute("DROP TABLE " + Record.getTableName(t));
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    static ConstructorHandle cacheConstructorHandle(Class<?> clazz) {
        Constructor<?> constructor = null;

        for (var c : clazz.getConstructors()) {
            if (c.isAnnotationPresent(RecordBuilder.class)) {
                constructor = c;
                break;
            }
        }

        if (constructor == null) {
            return null;
        }

        var paramAnnotations = constructor.getParameterAnnotations();
        var paramTypes = constructor.getParameterTypes();

        var parameterHandles = new ArrayList<ParameterHandle>();

        for (int i = 0; i < constructor.getParameterCount(); i++) {
            var fieldName = Arrays.stream(paramAnnotations[i])
                    .filter(a -> a instanceof Column)
                    .findAny()
                    .map(a -> ((Column) a).value())
                    .orElseThrow(RuntimeException::new);
            parameterHandles.add(new ParameterHandle(fieldName, paramTypes[i]));
        }

        if (parameterHandles.isEmpty()) {
            throw new IllegalArgumentException("Constructor builder must have parameters");
        }

        var lookup = MethodHandles.publicLookup();
        try {
            var handle = lookup.unreflectConstructor(constructor);
            return new ConstructorHandle(handle, parameterHandles);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
