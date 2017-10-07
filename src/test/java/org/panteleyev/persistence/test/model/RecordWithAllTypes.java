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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.Table;
import org.panteleyev.persistence.test.Base;
import org.panteleyev.persistence.test.EnumType;

@Table("all_types_table")
public class RecordWithAllTypes implements Record {
    private Integer id;

    // fields
    private String a;
    private Integer b;
    private Boolean c;
    private Date d;
    private Long e;
    private BigDecimal f;
    private EnumType g;
    private LocalDate h;

    public RecordWithAllTypes() {
    }

    public RecordWithAllTypes(Integer id, String a, Integer b, Boolean c, Date d, Long e, BigDecimal f, EnumType g, LocalDate h) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
        this.g = g;
        this.h = h;
    }

    @Field(value = Field.ID, primaryKey = true)
    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Field("a")
    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    @Field("b")
    public Integer getB() {
        return b;
    }

    public void setB(Integer b) {
        this.b = b;
    }

    @Field("c")
    public Boolean getC() {
        return c;
    }

    public void setC(Boolean c) {
        this.c = c;
    }

    @Field("d")
    public Date getD() {
        return d;
    }

    public void setD(Date d) {
        this.d = d;
    }

    @Field("e")
    public Long getE() {
        return e;
    }

    public void setE(Long e) {
        this.e = e;
    }

    @Field("f")
    public BigDecimal getF() {
        return f;
    }

    public void setF(BigDecimal f) {
        this.f = f;
    }

    @Field("g")
    public EnumType getG() {
        return g;
    }

    public void setG(EnumType g) {
        this.g = g;
    }

    @Field("h")
    public LocalDate getH() {
        return h;
    }

    public void setH(LocalDate h) {
        this.h = h;
    }

    public static RecordWithAllTypes newRecord(Integer id, Random random) {
        return new RecordWithAllTypes(
                id,
                UUID.randomUUID().toString(),
                random.nextInt(),
                random.nextBoolean(),
                new Date(),
                random.nextLong(),
                BigDecimal.TEN,
                EnumType.F2,
                LocalDate.now()
        );
    }

    public static RecordWithAllTypes newNullRecord(Integer id) {
        return new RecordWithAllTypes(
                id,
                null,
                null,
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
        if (o instanceof RecordWithAllTypes) {
            RecordWithAllTypes that = (RecordWithAllTypes) o;

            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.a, that.a)
                    && Objects.equals(this.b, that.b)
                    && Objects.equals(this.c, that.c)
                    && Objects.equals(this.d, that.d)
                    && Objects.equals(this.e, that.e)
                    && Base.compareBigDecimals(this.f, that.f)
                    && Objects.equals(this.g, that.g)
                    && Objects.equals(this.h, that.h);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, c, d, e, f, g, h);
    }
}
