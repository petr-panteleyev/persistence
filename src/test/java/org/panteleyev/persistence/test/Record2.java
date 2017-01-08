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

import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.panteleyev.persistence.Record;
import org.panteleyev.persistence.annotations.Field;
import org.panteleyev.persistence.annotations.Table;

@Table(name="table2", preload = true)
public class Record2 implements Record {
    private Integer id;

    // fields
    private String a;
    private Boolean b;

    public Record2() {
    }

    public Record2(String a, Boolean b) {
        this.a = a;
        this.b = b;
    }

    @Field(name = Field.ID, primaryKey = true)
    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Field(name = "a")
    public String getA() {
        return a;
    }

    public void setA(String a) {
        this.a = a;
    }

    @Field(name = "b")
    public Boolean getB() {
        return b;
    }

    public void setB(Boolean b) {
        this.b = b;
    }

    public static Record2 newRandomRecord(Random random) {
        return new Record2(
            UUID.randomUUID().toString(),
            random.nextBoolean()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Record2) {
            Record2 that = (Record2)o;

            return Objects.equals(this.id, that.id)
                && Objects.equals(this.a, that.a)
                && Objects.equals(this.b, that.b);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.id);
        hash = 79 * hash + Objects.hashCode(this.a);
        hash = 79 * hash + Objects.hashCode(this.b);
        return hash;
    }
}
