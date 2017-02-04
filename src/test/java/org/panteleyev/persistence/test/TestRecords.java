/*
 *  Copyright (c) 2016, 2017, Petr Panteleyev <petr@panteleyev.org>
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
package org.panteleyev.persistence.test;

import org.panteleyev.persistence.Record;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestRecords extends Base {
    private static final int RECORD_COUNT_1 = 10;
    private static final int RECORD_COUNT_2 = 10;

    private static final List<Class<? extends Record>> ALL_CLASSES =
        Arrays.asList(RecordWithAllTypes.class, RecordWithOptionals.class);

    @BeforeMethod
    public void setup() throws Exception {
        super.setupAndSkip();
    }

    @AfterMethod
    @Override
    public void cleanup() throws Exception {
        super.cleanup();
    }

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
