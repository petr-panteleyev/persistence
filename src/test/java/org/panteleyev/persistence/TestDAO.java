/*
 * Copyright (c) 2018, Petr Panteleyev <petr@panteleyev.org>
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
import org.panteleyev.persistence.test.EnumType;
import org.panteleyev.persistence.test.model.ImmutableRecord;
import org.panteleyev.persistence.test.model.RecordWithAllTypes;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.lang.invoke.VarHandle;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDAO {
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

        DAO dao = new DAO(new MySQLProxy());

        for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
            loadedRecords[i] = dao.fromSQL(resultSets[i], ImmutableRecord.class);
        }

        Assert.assertEquals(loadedRecords, records);
    }

    @Test
    public void testCacheConstructorHandle() {
        DAO.ConstructorHandle constructorHandle = DAO.cacheConstructorHandle(ImmutableRecord.class);

        Assert.assertNotNull(constructorHandle);
        Assert.assertNotNull(constructorHandle.handle);

        var parameters = constructorHandle.parameters;

        Assert.assertEquals(parameters.size(), EXPECTED_PARAMS.size());

        for (int i = 0; i < EXPECTED_PARAMS.size(); i++) {
            var p = parameters.get(i);
            Assert.assertEquals(p.name, EXPECTED_PARAMS.get(i).name);
            Assert.assertEquals(p.type, EXPECTED_PARAMS.get(i).type);
        }
    }

    @Test
    public void testCacheConstructorHandleNegative() {
        Assert.assertNull(DAO.cacheConstructorHandle(RecordWithAllTypes.class));
    }

    @Test
    public void testComputeColumns() {
        Map<String, VarHandle> actual = DAO.computeColumns(ImmutableRecord.class);

        for (var key : EXPECTED_PARAMS) {
            var handle = actual.get(key.name);
            Assert.assertNotNull(handle);
        }
    }

    @Test
    public void testPrimaryKeys() {
        DAO dao = new DAO();

        Integer key = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(key);
        Assert.assertEquals((int) key, 1);

        Integer newKey = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(newKey);
        Assert.assertEquals((int) newKey, 2);

        dao.resetPrimaryKey(ImmutableRecord.class);

        Integer resetKey = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(resetKey);
        Assert.assertEquals((int) resetKey, 1);
    }
}