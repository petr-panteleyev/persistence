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

package org.panteleyev.persistence.test.model;

import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.ForeignKey;
import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.annotations.ReferenceOption;
import org.panteleyev.persistence.annotations.Table;

@Table("child_table")
public class ChildTable implements Record {
    private int id;

    private final String nullValue;
    private final String cascadeValue;
    private final String restrictValue;
    private final String noActionValue;
    private final String noneValue;

    @RecordBuilder
    public ChildTable(
            @Field("id") int id,
            @Field("null_value") String nullValue,
            @Field("cascade_value") String cascadeValue,
            @Field("restrict_value") String restrictValue,
            @Field("no_action_value") String noActionValue,
            @Field("none_value") String noneValue
    ) {
        this.id = id;
        this.nullValue = nullValue;
        this.cascadeValue = cascadeValue;
        this.restrictValue = restrictValue;
        this.noActionValue = noActionValue;
        this.noneValue = noneValue;
    }

    @Field(value = Field.ID, primaryKey = true)
    @Override
    public int getId() {
        return id;
    }

    @Field(value="null_value")
    @ForeignKey(table = ParentTable.class, field = "value",
            onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.SET_NULL)
    public String getNullValue() {
        return nullValue;
    }

    @Field(value="cascade_value")
    @ForeignKey(table = ParentTable.class, field = "value",
            onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    public String getCascadeValue() {
        return cascadeValue;
    }

    @Field("restrict_value")
    @ForeignKey(table = ParentTable.class, field = "value",
            onDelete = ReferenceOption.RESTRICT, onUpdate = ReferenceOption.RESTRICT)
    public String getRestrictValue() {
        return restrictValue;
    }

    @Field(value="no_action_value")
    @ForeignKey(table = ParentTable.class, field = "value",
            onDelete = ReferenceOption.NO_ACTION, onUpdate = ReferenceOption.NO_ACTION)
    public String getNoActionValue() {
        return noActionValue;
    }

    @Field(value="none_value")
    @ForeignKey(table=ParentTable.class, field = "value")
    public String getNoneValue() {
        return noneValue;
    }
}
