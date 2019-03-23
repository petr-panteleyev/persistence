/*
 * Copyright (c) 2018, 2019, Petr Panteleyev <petr@panteleyev.org>
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

import org.panteleyev.persistence.answers.ResultSetBigDecimalAnswer;
import org.panteleyev.persistence.answers.ResultSetBooleanAnswer;
import org.panteleyev.persistence.answers.ResultSetIntAnswer;
import org.panteleyev.persistence.answers.ResultSetLongAnswer;
import org.panteleyev.persistence.answers.ResultSetObjectAnswer;
import org.panteleyev.persistence.model.EnumType;
import org.panteleyev.persistence.model.ImmutableRecord;
import org.panteleyev.persistence.model.RecordWithAllTypes;
import org.panteleyev.persistence.model.RecordWithJson;
import org.panteleyev.persistence.model.RecordWithOptionals;
import org.panteleyev.persistence.model.RecordWithUuid;
import org.panteleyev.persistence.model.UuidPrimaryKeyRecord;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Random;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.panteleyev.persistence.base.Base.GENERIC_GROUP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

@Test(groups = GENERIC_GROUP)
public class DaoTest {
    private static final int NUMBER_OF_RECORDS = 100;
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private static final List<DAO.ParameterHandle> EXPECTED_PARAMS = List.of(
        new DAO.ParameterHandle("id", Integer.class),
        new DAO.ParameterHandle("a", String.class),
        new DAO.ParameterHandle("b", Integer.class),
        new DAO.ParameterHandle("c", Boolean.class),
        new DAO.ParameterHandle("d", Date.class),
        new DAO.ParameterHandle("e", Long.class),
        new DAO.ParameterHandle("f", BigDecimal.class),
        new DAO.ParameterHandle("g", EnumType.class),
        new DAO.ParameterHandle("h", LocalDate.class)
    );

    @Test
    public void testFromSQLImmutable() throws Exception {
        var records = new ImmutableRecord[NUMBER_OF_RECORDS];
        var resultSets = new ResultSet[NUMBER_OF_RECORDS];

        for (int id = 1; id <= NUMBER_OF_RECORDS; id++) {
            var record = ImmutableRecord.newRecord(id, RANDOM);

            records[id - 1] = record;

            var rs = mock(ResultSet.class);
            when(rs.getObject(anyString())).then(new ResultSetObjectAnswer(record));
            when(rs.getLong(anyString())).then(new ResultSetLongAnswer(record));
            when(rs.getInt(anyString())).then(new ResultSetIntAnswer(record));
            when(rs.getBoolean(anyString())).then(new ResultSetBooleanAnswer(record));
            when(rs.getBigDecimal(anyString())).then(new ResultSetBigDecimalAnswer(record));

            resultSets[id - 1] = rs;
        }

        var loadedRecords = new ImmutableRecord[NUMBER_OF_RECORDS];

        var dao = new DAO(new MySQLProxy());

        for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
            loadedRecords[i] = dao.fromSQL(resultSets[i], ImmutableRecord.class);
        }

        assertEquals(loadedRecords, records);
    }

    @Test
    public void testCacheConstructorHandle() {
        DAO.ConstructorHandle constructorHandle = DAO.cacheConstructorHandle(ImmutableRecord.class);

        Assert.assertNotNull(constructorHandle);
        Assert.assertNotNull(constructorHandle.handle);

        var parameters = constructorHandle.parameters;

        assertEquals(parameters.size(), EXPECTED_PARAMS.size());

        for (int i = 0; i < EXPECTED_PARAMS.size(); i++) {
            var p = parameters.get(i);
            assertEquals(p.name, EXPECTED_PARAMS.get(i).name);
            assertEquals(p.type, EXPECTED_PARAMS.get(i).type);
        }
    }

    @Test
    public void testCacheConstructorHandleNegative() {
        Assert.assertNull(DAO.cacheConstructorHandle(RecordWithAllTypes.class));
    }

    @Test
    public void testComputeColumns() {
        var actual = DAO.computeColumns(ImmutableRecord.class);

        for (var key : EXPECTED_PARAMS) {
            var handle = actual.get(key.name);
            Assert.assertNotNull(handle);
        }
    }

    @DataProvider(name = "testGetSelectAllSqlDataProvider")
    public Object[][] testGetSelectAllSqlDataProvider() {
        return new Object[][]{
            // SQLite
            {new DAO(new SQLiteProxy()), RecordWithAllTypes.class,
                "SELECT primary_key,a_field,b_field,c_field,d_field,e_field,f_field,g_field,h_field FROM " +
                    "all_types_table"},
            {new DAO(new SQLiteProxy()), ImmutableRecord.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM immutable_table"},
            {new DAO(new SQLiteProxy()), RecordWithJson.class,
                "SELECT id,json FROM table_with_json"},
            {new DAO(new SQLiteProxy()), RecordWithUuid.class,
                "SELECT id,uuid FROM table_with_uuid"},
            {new DAO(new SQLiteProxy()), RecordWithOptionals.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM optionals_table"},

            // MySQL
            {new DAO(new MySQLProxy()), RecordWithAllTypes.class,
                "SELECT primary_key,a_field,b_field,c_field,d_field,e_field,f_field,g_field,h_field FROM " +
                    "all_types_table"},
            {new DAO(new MySQLProxy()), ImmutableRecord.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM immutable_table"},
            {new DAO(new MySQLProxy()), RecordWithJson.class,
                "SELECT id,json FROM table_with_json"},
            {new DAO(new MySQLProxy()), RecordWithUuid.class,
                "SELECT id,BIN_TO_UUID(uuid) AS uuid FROM table_with_uuid"},
            {new DAO(new MySQLProxy()), RecordWithOptionals.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM optionals_table"},
        };
    }


    @Test(dataProvider = "testGetSelectAllSqlDataProvider")
    public void testGetSelectAllSql(DAO dao, Class<? extends Record> clazz, String expected) {
        var sql = dao.getSelectAllSql(clazz);
        var sql2 = dao.getSelectAllSql(clazz);

        assertSame(sql, sql2);
        assertEquals(sql, expected);
    }

    @DataProvider(name = "testGetSelectByIdSqlDataProvider")
    public Object[][] testGetSelectByIdSqlDataProvider() {
        return new Object[][]{
            // SQLite
            {new DAO(new SQLiteProxy()), RecordWithAllTypes.class,
                "SELECT primary_key,a_field,b_field,c_field,d_field,e_field,f_field,g_field,h_field FROM " +
                    "all_types_table WHERE primary_key=?"},
            {new DAO(new SQLiteProxy()), ImmutableRecord.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM immutable_table WHERE id=?"},
            {new DAO(new SQLiteProxy()), RecordWithJson.class,
                "SELECT id,json FROM table_with_json WHERE id=?"},
            {new DAO(new SQLiteProxy()), RecordWithUuid.class,
                "SELECT id,uuid FROM table_with_uuid WHERE id=?"},
            {new DAO(new SQLiteProxy()), UuidPrimaryKeyRecord.class,
                "SELECT prim_key,value FROM uuid_primary_key WHERE prim_key=?"},

            // MySQL
            {new DAO(new MySQLProxy()), RecordWithAllTypes.class,
                "SELECT primary_key,a_field,b_field,c_field,d_field,e_field,f_field,g_field,h_field FROM " +
                    "all_types_table WHERE primary_key=?"},
            {new DAO(new MySQLProxy()), ImmutableRecord.class,
                "SELECT id,a,b,c,d,e,f,g,h FROM immutable_table WHERE id=?"},
            {new DAO(new MySQLProxy()), RecordWithJson.class,
                "SELECT id,json FROM table_with_json WHERE id=?"},
            {new DAO(new MySQLProxy()), RecordWithUuid.class,
                "SELECT id,BIN_TO_UUID(uuid) AS uuid FROM table_with_uuid WHERE id=?"},
            {new DAO(new MySQLProxy()), UuidPrimaryKeyRecord.class,
                "SELECT BIN_TO_UUID(prim_key) AS prim_key,value FROM uuid_primary_key WHERE BIN_TO_UUID(prim_key)=?"},
        };
    }

    @Test(dataProvider = "testGetSelectByIdSqlDataProvider")
    public void testGetSelectByIdSql(DAO dao, Class<? extends Record> clazz, String expected) {
        var sql = dao.getSelectByIdSql(clazz);
        var sql2 = dao.getSelectByIdSql(clazz);

        assertSame(sql, sql2);
        assertEquals(sql, expected);
    }

    @DataProvider(name = "testGetDeleteSqlDataProvider")
    public Object[][] testGetDeleteSqlDataProvider() {
        return new Object[][]{
            // SQLite
            {new DAO(new SQLiteProxy()), RecordWithAllTypes.class,
                "DELETE FROM all_types_table WHERE primary_key=?"},
            {new DAO(new SQLiteProxy()), ImmutableRecord.class,
                "DELETE FROM immutable_table WHERE id=?"},
            {new DAO(new SQLiteProxy()), RecordWithJson.class,
                "DELETE FROM table_with_json WHERE id=?"},
            {new DAO(new SQLiteProxy()), RecordWithUuid.class,
                "DELETE FROM table_with_uuid WHERE id=?"},

            // MySQL
            {new DAO(new MySQLProxy()), RecordWithAllTypes.class,
                "DELETE FROM all_types_table WHERE primary_key=?"},
            {new DAO(new MySQLProxy()), ImmutableRecord.class,
                "DELETE FROM immutable_table WHERE id=?"},
            {new DAO(new MySQLProxy()), RecordWithJson.class,
                "DELETE FROM table_with_json WHERE id=?"},
            {new DAO(new MySQLProxy()), RecordWithUuid.class,
                "DELETE FROM table_with_uuid WHERE id=?"},
        };
    }

    @Test(dataProvider = "testGetDeleteSqlDataProvider")
    public void testGetDeleteSql(DAO dao, Class<? extends Record> clazz, String expected) {
        var sql = dao.getDeleteSQL(clazz);
        var sql2 = dao.getDeleteSQL(clazz);

        assertSame(sql, sql2);
        assertEquals(sql, expected);
    }
}