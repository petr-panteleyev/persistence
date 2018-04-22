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

import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.answers.ResultSetBigDecimalAnswer;
import org.panteleyev.persistence.answers.ResultSetBooleanAnswer;
import org.panteleyev.persistence.answers.ResultSetIntAnswer;
import org.panteleyev.persistence.answers.ResultSetLongAnswer;
import org.panteleyev.persistence.answers.ResultSetObjectAnswer;
import org.panteleyev.persistence.test.model.ImmutableRecord;
import org.panteleyev.persistence.test.model.RecordWithAllTypes;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestDAO {
    private static final int NUMBER_OF_RECORDS = 100;
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private static final List<String> EXPECTED_PARAMS = List.of("id", "a", "b", "c", "d", "e", "f", "g", "h");

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
    public void testComputeParameters() {
        for (var constructor : ImmutableRecord.class.getConstructors()) {
            if (constructor.getAnnotation(RecordBuilder.class) != null) {
                var params = DAO.computeParameters(constructor);
                Assert.assertEquals(params, EXPECTED_PARAMS);
            }
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testComputeParametersNegative() {
        for (var constructor : RecordWithAllTypes.class.getConstructors()) {
            if (constructor.getParameterCount() > 0) {
                DAO.computeParameters(constructor);
            }
        }
    }

    @Test
    public void testComputeColumns() {
        Map<String, DAO.FieldRecord> actual = DAO.computeColumns(ImmutableRecord.class);

        for (var key : EXPECTED_PARAMS) {
            DAO.FieldRecord fieldRecord = actual.get(key);
            Assert.assertNotNull(fieldRecord);

            Assert.assertTrue(fieldRecord.offset > 0);
        }
    }

    @Test
    public void testPrimaryKeys() {
        DAO dao = new DAO();

        Integer key = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(key);
        Assert.assertEquals((int)key, 1);

        Integer newKey = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(newKey);
        Assert.assertEquals((int)newKey, 2);

        dao.resetPrimaryKey(ImmutableRecord.class);

        Integer resetKey = dao.generatePrimaryKey(ImmutableRecord.class);
        Assert.assertNotNull(resetKey);
        Assert.assertEquals((int)resetKey, 1);
    }
}