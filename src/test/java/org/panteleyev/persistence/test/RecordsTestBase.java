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
package org.panteleyev.persistence.test;

import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.test.model.BinaryRecord;
import org.panteleyev.persistence.test.model.ImmutableBinaryRecord;
import org.panteleyev.persistence.test.model.RecordWithAllTypes;
import org.panteleyev.persistence.test.model.RecordWithOptionals;
import org.panteleyev.persistence.test.model.RecordWithPrimitives;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RecordsTestBase extends Base {
    private static final int RECORD_COUNT_1 = 10;
    private static final int RECORD_COUNT_2 = 10;

    private static final List<Class<? extends Record>> ALL_CLASSES =
            Arrays.asList(RecordWithAllTypes.class, RecordWithOptionals.class,
                    ImmutableBinaryRecord.class, BinaryRecord.class);

    @Test(dataProvider = "recordClasses")
    public void testRecordCreation(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        Map<Integer, Record> idMap = new HashMap<>();

        // Create all new records
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            Record newRecord = givenRandomRecord(clazz);

            Record result = getDao().insert(newRecord);
            idMap.put(result.getId(), result);
        }

        checkCreatedRecord(clazz, idMap, RECORD_COUNT_1);
    }

    @Test
    public void testParallelRecordCreation() throws Exception {
        getDao().createTables(ALL_CLASSES);
        getDao().preload(ALL_CLASSES);

        final Map<Integer, Record> idMap1 = new HashMap<>();
        final Map<Integer, Record> idMap2 = new HashMap<>();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_1; i++) {
                try {
                    Record newRecord = givenRandomRecord(RecordWithAllTypes.class);
                    Record result = getDao().insert(newRecord);
                    idMap1.put(result.getId(), result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_2; i++) {
                try {
                    Record newRecord = givenRandomRecord(RecordWithOptionals.class);
                    Record result = getDao().insert(newRecord);
                    idMap2.put(result.getId(), result);
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
            Record record = givenRandomRecord(clazz);

            Record result = getDao().insert(record);
            Assert.assertEquals(result, record);
        }
    }

    @Test(dataProvider = "recordClasses")
    public void testRecordPutDelete(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        // Delete by record
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            Record record = givenRandomRecord(clazz);
            Record result = getDao().insert(record);
            Assert.assertEquals(result, record);

            getDao().delete(record);
            result = getDao().get(record.getId(), clazz);
            Assert.assertNull(result);
        }

        // Delete by id
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            Record record = givenRandomRecord(clazz);
            Record result = getDao().insert(record);
            Assert.assertEquals(result, record);

            getDao().delete(record.getId(), clazz);
            result = getDao().get(record.getId(), clazz);
            Assert.assertNull(result);
        }
    }

    @Test(dataProvider = "recordClasses")
    public void testRecordUpdate(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        Record original = givenRandomRecord(clazz);

        getDao().insert(original);

        Record updated = givenRandomRecordWithId(clazz, original.getId());

        Record updateResult = getDao().update(updated);
        Assert.assertEquals(updateResult.getId(), original.getId());

        Record retrievedUpdated = getDao().get(original.getId(), clazz);
        Assert.assertEquals(retrievedUpdated, updateResult);
        Assert.assertEquals(retrievedUpdated, updated);
    }

    @Test(dataProvider = "recordClasses")
    public void testNullFields(Class<? extends Record> clazz) throws Exception {
        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        Record record = givenNullRecord(clazz);

        Record insertResult = getDao().insert(record);
        Assert.assertEquals(insertResult, record);

        Record retrieved = getDao().get(record.getId(), clazz);
        Assert.assertEquals(retrieved, record);
    }

    @Test
    public void testExtremeValues() throws Exception {
        Class<? extends Record> clazz = RecordWithPrimitives.class;

        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        // Max values
        Integer idMax = getDao().generatePrimaryKey(clazz);
        Record rMax = new RecordWithPrimitives(idMax,
                Integer.MAX_VALUE,
                RANDOM.nextBoolean(),
                Long.MAX_VALUE);
        Record insertResultMax = getDao().insert(rMax);
        Assert.assertEquals(insertResultMax, rMax);

        Record retrievedMax = getDao().get(rMax.getId(), clazz);
        Assert.assertEquals(retrievedMax, rMax);

        // Min values
        Integer idMin = getDao().generatePrimaryKey(clazz);
        Record rMin = new RecordWithPrimitives(idMin,
                Integer.MIN_VALUE,
                RANDOM.nextBoolean(),
                Long.MIN_VALUE);
        Record insertResultMin = getDao().insert(rMin);
        Assert.assertEquals(insertResultMin, rMin);

        Record retrievedMin = getDao().get(rMin.getId(), clazz);
        Assert.assertEquals(retrievedMin, rMin);
    }

    @DataProvider(name = "testBatchInsert")
    public Object[][] testBatchInsertDataProvider() {
        return new Object[][]{
                {100, 7},
                {100, 10}
        };
    }

    @Test(dataProvider = "testBatchInsert")
    public void testBatchInsert(int count, int batchSize) throws Exception {
        Class<RecordWithPrimitives> clazz = RecordWithPrimitives.class;

        getDao().createTables(Collections.singletonList(clazz));
        getDao().preload(Collections.singletonList(clazz));

        // Create records
        List<RecordWithPrimitives> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(RecordWithPrimitives.newRecord(getDao().generatePrimaryKey(clazz), RANDOM));
        }

        // Insert records
        getDao().insert(batchSize, records);

        // Retrieve records
        List<RecordWithPrimitives> retrieved = getDao().getAll(clazz);

        // Size must be the same
        Assert.assertEquals(retrieved.size(), records.size());
    }

    @Test
    public void testTruncate() throws Exception {
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

        Assert.assertEquals(getDao().getAll(RecordWithAllTypes.class).size(), 5);
        Assert.assertEquals(getDao().getAll(RecordWithPrimitives.class).size(), 3);

        Assert.assertNotEquals(getDao().generatePrimaryKey(RecordWithAllTypes.class), 1);
        Assert.assertNotEquals(getDao().generatePrimaryKey(RecordWithPrimitives.class), 1);

        getDao().truncate(classes);

        Assert.assertEquals(getDao().getAll(RecordWithAllTypes.class).size(), 0);
        Assert.assertEquals(getDao().getAll(RecordWithPrimitives.class).size(), 0);

        Assert.assertTrue(getDao().generatePrimaryKey(RecordWithAllTypes.class) == 1);
        Assert.assertTrue(getDao().generatePrimaryKey(RecordWithPrimitives.class) == 1);
    }

    private <T extends Record> void checkCreatedRecord(Class<T> clazz, Map<Integer, Record> idMap, int count) {
        // Get all records back in one request
        List<T> result = getDao().getAll(clazz);

        // Check total amount or records returned
        Assert.assertEquals(result.size(), count);
        Assert.assertEquals(result.size(), idMap.keySet().size());

        // Check uniqueness of all primary keys
        Assert.assertEquals(result.stream()
                .map(Record::getId)
                .distinct()
                .count(), count);
    }
}
