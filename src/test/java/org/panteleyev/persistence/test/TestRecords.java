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
package org.panteleyev.persistence.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.panteleyev.persistence.Record;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestRecords extends Base {
    private static final int RECORD_COUNT_1 = 10;
    private static final int RECORD_COUNT_2 = 10;

    private static final List<Class<? extends Record>> ALL_CLASSES =
        Arrays.asList(RecordWithAllTypes.class, Record2.class);

    @BeforeMethod
    public void setup() throws Exception {
        super.setupAndSkip();
    }

    @AfterMethod
    @Override
    public void cleanup() throws Exception {
        super.cleanup();
    }

    @Test(dataProvider = "cacheNoCache")
    public void testRecordCreation(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        Map<Integer, Record> idMap = new HashMap<>();

        // Create all new records
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            RecordWithAllTypes newRecord = RecordWithAllTypes.newRecord(RANDOM);
            Integer id = getDao().put(newRecord);
            idMap.put(id, newRecord);
        }

        checkCreatedRecord(RecordWithAllTypes.class, idMap, RECORD_COUNT_1);
    }

    @Test(dataProvider = "cacheNoCache")
    public void testParallelRecordCreation(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(ALL_CLASSES);
        getDao().preload(ALL_CLASSES);

        final Map<Integer, Record> idMap1 = new HashMap<>();
        final Map<Integer, Record> idMap2 = new HashMap<>();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_1; i++) {
                RecordWithAllTypes newRecord = RecordWithAllTypes.newRecord(RANDOM);
                Integer id = getDao().put(newRecord);
                idMap1.put(id, newRecord);
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < RECORD_COUNT_2; i++) {
                Record2 newRecord = new Record2(UUID.randomUUID().toString(), RANDOM.nextBoolean());
                Integer id = getDao().put(newRecord);
                idMap2.put(id, newRecord);
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        checkCreatedRecord(RecordWithAllTypes.class, idMap1, RECORD_COUNT_1);
        checkCreatedRecord(Record2.class, idMap2, RECORD_COUNT_2);
    }

    @Test(dataProvider = "cacheNoCache")
    public void testRecordPutGet(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        for (int i = 0; i < RECORD_COUNT_1; i++) {
            RecordWithAllTypes record = RecordWithAllTypes.newRecord(RANDOM);

            Integer id = getDao().put(record);
            RecordWithAllTypes result = getDao().get(id, RecordWithAllTypes.class);

            record.setId(id);
            Assert.assertEquals(result, record);
        }
    }

    @Test(dataProvider = "cacheNoCache")
    public void testRecordPutDelete(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        // Delete by record
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            RecordWithAllTypes record = RecordWithAllTypes.newRecord(RANDOM);

            Integer id = getDao().put(record);
            RecordWithAllTypes result = getDao().get(id, RecordWithAllTypes.class);

            record.setId(id);
            Assert.assertEquals(result, record);

            getDao().delete(record);
            result = getDao().get(id, RecordWithAllTypes.class);
            Assert.assertNull(result);
        }

        // Delete by id
        for (int i = 0; i < RECORD_COUNT_1; i++) {
            RecordWithAllTypes record = RecordWithAllTypes.newRecord(RANDOM);

            Integer id = getDao().put(record);
            RecordWithAllTypes result = getDao().get(id, RecordWithAllTypes.class);

            record.setId(id);
            Assert.assertEquals(result, record);

            getDao().delete(id, RecordWithAllTypes.class);
            result = getDao().get(id, RecordWithAllTypes.class);
            Assert.assertNull(result);
        }
    }

    @Test(dataProvider = "cacheNoCache")
    public void testRecordPutDeletePreloaded(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(Record2.class));
        getDao().preload(Arrays.asList(Record2.class));

        // Delete by record
        for (int i = 0; i < RECORD_COUNT_2; i++) {
            Record2 record = new Record2(UUID.randomUUID().toString(), RANDOM.nextBoolean());

            Integer id = getDao().put(record);
            Record2 result = getDao().get(id, Record2.class);

            record.setId(id);
            Assert.assertEquals(result, record);

            getDao().delete(record);
            result = getDao().get(id, Record2.class);
            Assert.assertNull(result);
        }

        // Delete by id
        for (int i = 0; i < RECORD_COUNT_2; i++) {
            Record2 record = new Record2(UUID.randomUUID().toString(), RANDOM.nextBoolean());

            Integer id = getDao().put(record);
            Record2 result = getDao().get(id, Record2.class);

            record.setId(id);
            Assert.assertEquals(result, record);

            getDao().delete(id, Record2.class);
            result = getDao().get(id, Record2.class);
            Assert.assertNull(result);
        }
    }

    @Test(dataProvider = "cacheNoCache")
    public void testRecordUpdate(boolean useCache) {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        RecordWithAllTypes original = RecordWithAllTypes.newRecord(RANDOM);

        Integer id = getDao().put(original);

        RecordWithAllTypes updated = RecordWithAllTypes.newRecord(RANDOM);
        updated.setId(id);

        Integer updatedId = getDao().put(updated);
        Assert.assertEquals(updatedId, id);

        RecordWithAllTypes retrievedUpdated = getDao().get(id, RecordWithAllTypes.class);
        Assert.assertEquals(retrievedUpdated, updated);
    }

    @Test(dataProvider = "cacheNoCache")
    public void testNullFields(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        RecordWithAllTypes record = RecordWithAllTypes.newNullRecord();

        Integer id = getDao().put(record);

        RecordWithAllTypes retrieved = getDao().get(id, RecordWithAllTypes.class);
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
            .map(x -> x.getId())
            .distinct()
            .count(), count);
    }
}
