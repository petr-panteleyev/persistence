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

package org.panteleyev.persistence.test.model;

import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.annotations.Column;
import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.annotations.Table;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

@Table("binary_table")
public class BinaryRecord implements Record {
    @Column(value = Column.ID, primaryKey = true)
    private Integer id;

    @Column(value = "a", length = 3000)
    private byte[] a;

    public BinaryRecord() {
    }

    private BinaryRecord(int id, byte[] a) {
        this.id = id;
        this.a = a;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object instanceof BinaryRecord) {
            BinaryRecord that = (BinaryRecord) object;
            return Objects.equals(this.id, that.id)
                    && Arrays.equals(this.a, that.a);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, a);
    }

    public static BinaryRecord newRecord(Integer id, Random random) {
        byte[] a = new byte[1000];
        random.nextBytes(a);

        return new BinaryRecord(id, a);
    }

    public static BinaryRecord newNullRecord(Integer id) {
        return new BinaryRecord(id, null);
    }
}
