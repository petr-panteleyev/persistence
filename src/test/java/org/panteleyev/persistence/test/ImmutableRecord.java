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
import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.RecordBuilder;
import org.panteleyev.persistence.annotations.Table;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@Table("immutable_table")
public class ImmutableRecord implements Record {
    private final Integer id;

    // fields
    private final String a;
    private final Integer b;
    private final Boolean c;
    private final Date d;
    private final Long e;
    private final BigDecimal f;

    @RecordBuilder
    public ImmutableRecord(
            @Field("id") Integer id,
            @Field("a") String a,
            @Field("b") Integer b,
            @Field("c") Boolean c,
            @Field("d") Date d,
            @Field("e") Long e,
            @Field("f") BigDecimal f) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    @Field(value = Field.ID, primaryKey = true)
    @Override
    public Integer getId() {
        return id;
    }

    @Field(value = "a")
    public String getA() {
        return a;
    }

    @Field(value = "b")
    public Integer getB() {
        return b;
    }

    @Field(value = "c")
    public Boolean getC() {
        return c;
    }

    @Field(value = "d")
    public Date getD() {
        return d;
    }

    @Field(value = "e")
    public Long getE() {
        return e;
    }

    @Field(value = "f")
    public BigDecimal getF() {
        return f;
    }

    public static ImmutableRecord newRecord(Integer id, Random random) {
        return new ImmutableRecord(
                id,
                UUID.randomUUID().toString(),
                random.nextInt(),
                random.nextBoolean(),
                new Date(),
                random.nextLong(),
                BigDecimal.TEN
        );
    }

    public static ImmutableRecord newNullRecord(Integer id) {
        return new ImmutableRecord(
                id,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImmutableRecord) {
            ImmutableRecord that = (ImmutableRecord)o;

            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.a, that.a)
                    && Objects.equals(this.b, that.b)
                    && Objects.equals(this.c, that.c)
                    && Objects.equals(this.d, that.d)
                    && Objects.equals(this.e, that.e)
                    && Objects.equals(this.f, that.f);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + Objects.hashCode(this.id);
        hash = 29 * hash + Objects.hashCode(this.a);
        hash = 29 * hash + Objects.hashCode(this.b);
        hash = 29 * hash + Objects.hashCode(this.c);
        hash = 29 * hash + Objects.hashCode(this.d);
        hash = 29 * hash + Objects.hashCode(this.e);
        hash = 29 * hash + Objects.hashCode(this.f);
        return hash;
    }
}
