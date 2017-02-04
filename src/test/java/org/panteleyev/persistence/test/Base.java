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

import java.io.File;
import java.lang.reflect.Method;
import java.util.Random;
import javax.sql.DataSource;
import org.panteleyev.persistence.DAO;
import org.panteleyev.persistence.Record;
import org.testng.annotations.DataProvider;

class Base {
    private static final Random RANDOM = new Random(System.currentTimeMillis());

    private File TEST_DB_FILE;

    private DAO dao;

    public void setupAndSkip() throws Exception {
        TEST_DB_FILE = File.createTempFile("persistence", "db");

        DataSource ds = new DAO.Builder()
            .file(TEST_DB_FILE.getAbsolutePath())
            .build();

        dao = new DAOImpl(ds);
    }

    public void cleanup() throws Exception {
        if (TEST_DB_FILE != null && TEST_DB_FILE.exists()) {
            TEST_DB_FILE.delete();
        }
    }

    public DAO getDao() {
        return dao;
    }

    @DataProvider(name = "recordClasses")
    public Object[][] recordClassesProvider() {
        return new Object[][] {
                { RecordWithAllTypes.class },
                { RecordWithOptionals.class },
                { ImmutableRecord.class },
        };
    }

    protected <T extends Record> T givenRandomRecord(Class<T> clazz) throws Exception {
        Integer id = dao.generatePrimaryKey(clazz);
        return givenRandomRecordWithId(clazz, id);
    }

    protected <T extends Record> T givenRandomRecordWithId(Class<T> clazz, Integer id) throws Exception {
        Method method = clazz.getDeclaredMethod("newRecord", Integer.class, Random.class);
        return (T)method.invoke(null, id, RANDOM);
    }

    protected <T extends Record> T givenNullRecord(Class<T> clazz) throws Exception {
        Integer id = dao.generatePrimaryKey(clazz);
        Method method = clazz.getDeclaredMethod("newNullRecord", Integer.class);
        return (T)method.invoke(null, id);
    }
}

