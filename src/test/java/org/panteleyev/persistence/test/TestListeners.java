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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.TableListener;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

class TestTableListener implements TableListener {
    boolean added;
    boolean updated;
    boolean deleted;

    Record record;

    void reset() {
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

    @Test
    public void testListenerCall() throws Exception {
        getDao().createTables(Arrays.asList(RecordWithAllTypes.class, RecordWithOptionals.class));
        getDao().preload(Arrays.asList(RecordWithAllTypes.class, RecordWithOptionals.class));

        List<TestTableListener> listeners = Arrays.asList(
            new TestTableListener(),
            new TestTableListener(),
            new TestTableListener()
        );

        listeners.forEach(l -> getDao().addListener(RecordWithAllTypes.class, l));

        // modify another table, listener must be intact
        RecordWithOptionals r2 = givenRandomRecord(RecordWithOptionals.class);
        getDao().insert(r2);
        assertListeners(listeners, false, false, false, null);
        r2.setA(UUID.randomUUID().toString());
        getDao().update(r2);
        assertListeners(listeners, false, false, false, null);
        getDao().delete(r2);
        assertListeners(listeners, false, false, false, null);

        // Manipulate with correct table
        RecordWithAllTypes r1 = givenRandomRecord(RecordWithAllTypes.class);

        getDao().insert(r1);
        assertListeners(listeners, true, false, false, r1);
        listeners.forEach(TestTableListener::reset);

        r1.setA(UUID.randomUUID().toString());
        getDao().update(r1);
        assertListeners(listeners, false, true, false, r1);
        listeners.forEach(TestTableListener::reset);

        getDao().delete(r1);
        assertListeners(listeners, false, false, true, r1);
    }

    @Test
    public void testListenerAddRemove() throws Exception {
        getDao().createTables(Collections.singletonList(RecordWithAllTypes.class));
        getDao().preload(Collections.singletonList(RecordWithAllTypes.class));

        TestTableListener listener = new TestTableListener();
        List<TestTableListener> listeners = Collections.singletonList(listener);

        getDao().addListener(RecordWithAllTypes.class, listener);
        getDao().removeListener(RecordWithAllTypes.class, listener);

        RecordWithAllTypes r1 = givenRandomRecord(RecordWithAllTypes.class);

        getDao().insert(r1);
        assertListeners(listeners, false, false, false, null);

        r1.setA(UUID.randomUUID().toString());
        getDao().update(r1);
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
