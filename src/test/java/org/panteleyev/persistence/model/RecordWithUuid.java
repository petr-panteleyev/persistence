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

package org.panteleyev.persistence.model;

import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.annotations.Column;
import org.panteleyev.persistence.annotations.PrimaryKey;
import org.panteleyev.persistence.annotations.Table;
import java.util.Objects;
import java.util.UUID;

@Table("table_with_uuid")
public class RecordWithUuid implements Record<Integer> {
    @PrimaryKey
    @Column(Column.ID)
    private int id;

    @Column(value = "uuid", isJson = true)
    private UUID uuid;

    public RecordWithUuid() {
    }

    public RecordWithUuid(int id, UUID uuid) {
        this.id = id;
        this.uuid = uuid;
    }

    public int getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof RecordWithUuid)) {
            return false;
        }

        var that = (RecordWithUuid) object;
        return this.id == that.id
            && Objects.equals(this.uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuid);
    }
}
