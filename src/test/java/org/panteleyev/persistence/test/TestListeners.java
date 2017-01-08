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
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.TableListener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

class TestTableListener implements TableListener {
    public boolean added;
    public boolean updated;
    public boolean deleted;

    public Record record;

    public void reset() {
        added = false;
        updated = false;
        deleted = false;
        record = null;
    }

    @Override
    public void recordAdded(Record record) {
        this.record = record;
        added = true;
    }

    @Override
    public void recordUpdated(Record record) {
        this.record = record;
        updated = true;
    }

    @Override
    public void recordDeleted(Record record) {
        this.record = record;
        deleted = true;
    }
}

public class TestListeners extends Base {
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
    public void testListenerCall(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class, Record2.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class, Record2.class));

        List<TestTableListener> listeners = Arrays.asList(
            new TestTableListener(),
            new TestTableListener(),
            new TestTableListener()
        );

        listeners.forEach(l -> getDao().addListener(RecordWithAllTypes.class, l));

        // modify another table, listener must be intact
        Record2 r2 = Record2.newRandomRecord(RANDOM);
        Integer id = getDao().put(r2);
        assertListeners(listeners, false, false, false, null);
        r2.setId(id);
        r2.setA(UUID.randomUUID().toString());
        getDao().put(r2);
        assertListeners(listeners, false, false, false, null);
        getDao().delete(r2);
        assertListeners(listeners, false, false, false, null);

        // Manipulate with correct table
        RecordWithAllTypes r1 = RecordWithAllTypes.newRecord(RANDOM);

        id = getDao().put(r1);
        r1.setId(id);
        assertListeners(listeners, true, false, false, r1);
        listeners.forEach(l -> l.reset());

        r1.setA(UUID.randomUUID().toString());
        getDao().put(r1);
        assertListeners(listeners, false, true, false, r1);
        listeners.forEach(l -> l.reset());

        getDao().delete(r1);
        assertListeners(listeners, false, false, true, r1);
    }

    @Test(dataProvider = "cacheNoCache")
    public void testListenerAddRemove(boolean useCache) throws Exception {
        getDao().setUseCache(useCache);

        getDao().createTables(Arrays.asList(RecordWithAllTypes.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class));

        TestTableListener listener = new TestTableListener();
        List<TestTableListener> listeners = Arrays.asList(listener);

        getDao().addListener(RecordWithAllTypes.class, listener);
        getDao().removeListener(RecordWithAllTypes.class, listener);

        RecordWithAllTypes r1 = RecordWithAllTypes.newRecord(RANDOM);

        Integer id = getDao().put(r1);
        r1.setId(id);
        assertListeners(listeners, false, false, false, null);

        r1.setA(UUID.randomUUID().toString());
        getDao().put(r1);
        assertListeners(listeners, false, false, false, null);

        getDao().delete(r1);
        assertListeners(listeners, false, false, false, null);
    }


    private void assertListeners(Collection<TestTableListener> listeners, boolean added, boolean updated, boolean deleted, Record record) {
        listeners.forEach(l -> {
            Assert.assertEquals(l.added, added);
            Assert.assertEquals(l.updated, updated);
            Assert.assertEquals(l.deleted, deleted);
            Assert.assertEquals(l.record, record);
        });
    }
}
