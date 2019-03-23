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
import org.panteleyev.persistence.model.StringPrimaryKeyRecord;
import org.testng.annotations.Test;
import java.util.Collections;
import java.util.UUID;
import static org.panteleyev.persistence.base.Base.MYSQL_GROUP;
import static org.panteleyev.persistence.base.Base.SQLITE_GROUP;
import static org.testng.Assert.assertEquals;

@Test(groups = {SQLITE_GROUP, MYSQL_GROUP})
public class StringPrimaryKeyTest extends Base {

    @Test
    public void testStringPrimaryKey() {
        getDao().createTables(Collections.singletonList(StringPrimaryKeyRecord.class));
        getDao().preload(Collections.singletonList(StringPrimaryKeyRecord.class));

        var id = UUID.randomUUID().toString();
        var record = new StringPrimaryKeyRecord(id, UUID.randomUUID().toString());

        getDao().insert(record);

        var retrieved = getDao().get(id, StringPrimaryKeyRecord.class);
        assertEquals(retrieved, record);

        var newValue = UUID.randomUUID().toString();
        getDao().update(new StringPrimaryKeyRecord(record.getPrimKey(), newValue));

        var updated = getDao().get(id, StringPrimaryKeyRecord.class);
        assertEquals(updated.getPrimKey(), record.getPrimKey());
        assertEquals(updated.getValue(), newValue);
    }
}
