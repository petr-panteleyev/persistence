/*
 * Copyright (c) 2019, Petr Panteleyev <petr@panteleyev.org>
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

import org.panteleyev.persistence.base.Base;
import org.panteleyev.persistence.model.BinaryRecord;
import org.panteleyev.persistence.model.ImmutableBinaryRecord;
import org.panteleyev.persistence.model.RecordWithAllTypes;
import org.panteleyev.persistence.model.RecordWithOptionals;
import org.panteleyev.persistence.model.RecordWithPrimitives;
import org.testng.annotations.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.panteleyev.persistence.base.Base.MYSQL_GROUP;
import static org.panteleyev.persistence.base.Base.SQLITE_GROUP;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

@Test(groups = {SQLITE_GROUP, MYSQL_GROUP})
public class RecordsTest extends Base {
    private static final int RECORD_COUNT_1 = 10;
    private static final int RECORD_COUNT_2 = 10;

    private static final List<Class<? extends Record>> ALL_CLASSES =
        Arrays.asList(RecordWithAllTypes.class, RecordWithOptionals.class,
            ImmutableBinaryRecord.class, BinaryRecord.class);

    @Test(dataProvider = "recordClasses")
    public void testRecordCreation(Class<? extends Record<Integer>> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        Map<Integer, Record<Integer>> idMap = new HashMap<>();

        // Create all new records
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            var newRecord = givenRandomRecord(clazz);

            getDao().insert(newRecord);
            idMap.put(newRecord.getPrimaryKey(), newRecord);
        }

        checkCreatedRecord(clazz, idMap, RECORD_COUNT_1);
    }

    @Test
    public void testParallelRecordCreation() throws Exception {
        getDao().createTables(ALL_CLASSES);
        getDao().preload(ALL_CLASSES);

        final Map<Integer, Record<Integer>> idMap1 = new HashMap<>();
        final Map<Integer, Record<Integer>> idMap2 = new HashMap<>();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_1; i++) {
                try {
                    var newRecord = givenRandomRecord(RecordWithAllTypes.class);
                    getDao().insert(newRecord);
                    idMap1.put(newRecord.getId(), newRecord);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_2; i++) {
                try {
                    var newRecord = givenRandomRecord(RecordWithOptionals.class);
                    getDao().insert(newRecord);
                    idMap2.put(newRecord.getId(), newRecord);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        checkCreatedRecord(RecordWithAllTypes.class, idMap1, RECORD_COUNT_1);
        checkCreatedRecord(RecordWithOptionals.class, idMap2, RECORD_COUNT_2);
    }

    @Test(dataProvider = "recordClasses")
    public void testRecordPutGet(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        for (int i = 0; i < RECORD_COUNT_1; i++) {
            var record = givenRandomRecord(clazz);

            getDao().insert(record);
            var result = getDao().get(record.getPrimaryKey(), clazz);
            assertEquals(result, record);
        }
    }

    @Test(dataProvider = "recordClasses")
    public void testRecordPutDelete(Class<? extends Record<Integer>> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        // Delete by record
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            var record = givenRandomRecord(clazz);
            getDao().insert(record);
            var result =  getDao().get(record.getPrimaryKey(), clazz);
            assertEquals(result, record);

            getDao().delete(record);
            result = getDao().get(record.getPrimaryKey(), clazz);
            assertNull(result);
        }

        // Delete by id
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            var record = givenRandomRecord(clazz);
            getDao().insert(record);
            var result =  getDao().get(record.getPrimaryKey(), clazz);
            assertEquals(result, record);

            getDao().delete(record.getPrimaryKey(), clazz);
            result = getDao().get(record.getPrimaryKey(), clazz);
            assertNull(result);
        }
    }

    @Test(dataProvider = "recordClasses")
    public void testRecordUpdate(Class<? extends Record<Integer>> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        var original = givenRandomRecord(clazz);

        getDao().insert(original);

        var updated = givenRandomRecordWithId(clazz, original.getPrimaryKey());

        getDao().update(updated);

        var retrievedUpdated = getDao().get(original.getPrimaryKey(), clazz);
        assertEquals(retrievedUpdated, updated);
    }

    @Test(dataProvider = "recordClasses")
    public void testNullFields(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        var record = givenNullRecord(clazz);

        getDao().insert(record);

        var retrieved = getDao().get(record.getPrimaryKey(), clazz);
        assertEquals(retrieved, record);
    }

    @Test
    public void testExtremeValues() {
        var clazz = RecordWithPrimitives.class;

        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        // Max values
        var idMax = getDao().generatePrimaryKey(clazz);
        var rMax = new RecordWithPrimitives(idMax,
            Integer.MAX_VALUE,
            RANDOM.nextBoolean(),
            Long.MAX_VALUE);
        getDao().insert(rMax);

        var retrievedMax = getDao().get(rMax.getId(), clazz);
        assertEquals(retrievedMax, rMax);

        // Min values
        var idMin = getDao().generatePrimaryKey(clazz);
        var rMin = new RecordWithPrimitives(idMin,
            Integer.MIN_VALUE,
            RANDOM.nextBoolean(),
            Long.MIN_VALUE);
        getDao().insert(rMin);

        var retrievedMin = getDao().get(rMin.getId(), clazz);
        assertEquals(retrievedMin, rMin);
    }

    @Test
    public void testTruncate() {
        List<Class<? extends Record>> classes = Arrays.asList(RecordWithAllTypes.class, RecordWithPrimitives.class);

        getDao().createTables(classes);
        getDao().preload(classes);

        List<Record> l1 = Arrays.asList(
            RecordWithAllTypes.newRecord(getDao().generatePrimaryKey(RecordWithAllTypes.class), RANDOM),
            RecordWithAllTypes.newRecord(getDao().generatePrimaryKey(RecordWithAllTypes.class), RANDOM),
            RecordWithAllTypes.newRecord(getDao().generatePrimaryKey(RecordWithAllTypes.class), RANDOM),
            RecordWithAllTypes.newRecord(getDao().generatePrimaryKey(RecordWithAllTypes.class), RANDOM),
            RecordWithAllTypes.newRecord(getDao().generatePrimaryKey(RecordWithAllTypes.class), RANDOM)
        );

        List<Record> l2 = Arrays.asList(
            RecordWithPrimitives.newRecord(getDao().generatePrimaryKey(RecordWithPrimitives.class), RANDOM),
            RecordWithPrimitives.newRecord(getDao().generatePrimaryKey(RecordWithPrimitives.class), RANDOM),
            RecordWithPrimitives.newRecord(getDao().generatePrimaryKey(RecordWithPrimitives.class), RANDOM)
        );

        getDao().insert(l1.size(), l1);
        getDao().insert(l2.size(), l2);

        assertEquals(getDao().getAll(RecordWithAllTypes.class).size(), 5);
        assertEquals(getDao().getAll(RecordWithPrimitives.class).size(), 3);

        assertNotEquals(getDao().generatePrimaryKey(RecordWithAllTypes.class), 1);
        assertNotEquals(getDao().generatePrimaryKey(RecordWithPrimitives.class), 1);

        getDao().truncate(classes);

        assertEquals(getDao().getAll(RecordWithAllTypes.class).size(), 0);
        assertEquals(getDao().getAll(RecordWithPrimitives.class).size(), 0);

        assertEquals((int) getDao().generatePrimaryKey(RecordWithAllTypes.class), 1);
        assertEquals((int) getDao().generatePrimaryKey(RecordWithPrimitives.class), 1);
    }

    private <T extends Record> void checkCreatedRecord(Class<T> clazz, Map<Integer, Record<Integer>> idMap,
                                                                int count) {
        // Get all records back in one request
        List<T> result = getDao().getAll(clazz);

        // Check total amount or records returned
        assertEquals(result.size(), count);
        assertEquals(result.size(), idMap.keySet().size());

        // Check uniqueness of all primary keys
        assertEquals(result.stream()
            .map(Record::getPrimaryKey)
            .distinct()
            .count(), count);
    }
}
