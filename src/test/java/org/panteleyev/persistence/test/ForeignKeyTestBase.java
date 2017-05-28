/*
 * Copyright (c) 2017, Petr Panteleyev <petr@panteleyev.org>
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
import org.panteleyev.persistence.test.model.ChildTable;
import org.panteleyev.persistence.test.model.ParentTable;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

class ForeignKeyTestBase extends Base {

    private void deleteForbidden(Record record) {
        boolean exception = false;
        try {
            getDao().delete(record);
        } catch (Exception ex) {
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    private void updateForbidden(ParentTable record) {
        boolean exception = false;
        try {
            record.setValue(UUID.randomUUID().toString());
            getDao().update(record);
        } catch (Exception ex) {
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    @Test
    public void testForeignKeyOnDelete() throws Exception {
        List<Class<? extends Record>> classes = Arrays.asList(ParentTable.class, ChildTable.class);

        getDao().createTables(classes);
        getDao().preload(classes);

        ParentTable cascade = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(cascade);

        ParentTable restrict = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(restrict);

        ParentTable setNull = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(setNull);

        ParentTable noAction = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(noAction);

        ParentTable none = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(none);

        ChildTable table = new ChildTable(
                getDao().generatePrimaryKey(ChildTable.class),
                setNull.getValue(),
                cascade.getValue(),
                restrict.getValue(),
                noAction.getValue(),
                none.getValue()
        );
        getDao().insert(table);

        deleteForbidden(none);
        deleteForbidden(noAction);
        deleteForbidden(restrict);

        // Set null
        getDao().delete(setNull);
        ChildTable setNullCheck = getDao().get(table.getId(), ChildTable.class);
        Assert.assertNotNull(setNullCheck);
        Assert.assertNull(setNullCheck.getNullValue());

        // Cascade
        getDao().delete(cascade);
        ChildTable cascadeCheck = getDao().get(table.getId(), ChildTable.class);
        Assert.assertNull(cascadeCheck);
    }

    @Test
    public void testForeignKeyOnUpdate() throws Exception {
        List<Class<? extends Record>> classes = Arrays.asList(ParentTable.class, ChildTable.class);

        getDao().createTables(classes);
        getDao().preload(classes);

        ParentTable cascade = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(cascade);

        ParentTable restrict = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(restrict);

        ParentTable setNull = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(setNull);

        ParentTable noAction = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(noAction);

        ParentTable none = new ParentTable(getDao().generatePrimaryKey(ParentTable.class),
                UUID.randomUUID().toString());
        getDao().insert(none);

        ChildTable table = new ChildTable(
                getDao().generatePrimaryKey(ChildTable.class),
                setNull.getValue(),
                cascade.getValue(),
                restrict.getValue(),
                noAction.getValue(),
                none.getValue()
        );
        getDao().insert(table);

        updateForbidden(none);
        updateForbidden(noAction);
        updateForbidden(restrict);

        // Set null
        setNull.setValue(UUID.randomUUID().toString());
        getDao().update(setNull);
        ChildTable setNullCheck = getDao().get(table.getId(), ChildTable.class);
        Assert.assertNotNull(setNullCheck);
        Assert.assertNull(setNullCheck.getNullValue());

        // Cascade
        cascade.setValue(UUID.randomUUID().toString());
        getDao().update(cascade);
        ChildTable cascadeCheck = getDao().get(table.getId(), ChildTable.class);
        Assert.assertEquals(cascadeCheck.getCascadeValue(), cascade.getValue());
    }

}
