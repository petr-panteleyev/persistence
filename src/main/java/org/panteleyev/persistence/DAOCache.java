/*
 * Copyright (c) 2015, Petr Panteleyev <petr@panteleyev.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
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

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

class DAOCache {
    private final Map<Class<? extends Record>, Map<Integer, Record>> cache = new HashMap<>();
    private final Map<Class<? extends Record>, Map<Integer, SoftReference<Record>>> softCache = new HashMap<>();

    public void put(Record record) {
        Map<Integer, Record> map = cache.computeIfAbsent(record.getClass(), k -> new HashMap<>());
        map.put(record.getId(), record);
    }

    public void putSoft(Record record) {
        Map<Integer, SoftReference<Record>> map =
            softCache.computeIfAbsent(record.getClass(), k -> new HashMap<>());

        map.put(record.getId(), new SoftReference(record));
    }

    public Record get(Class<? extends Record> clazz, Integer key) {
        Map<Integer, Record> map = cache.computeIfAbsent(clazz, k -> new HashMap<>());

        Record record = map.get(key);
        if (record != null) {
            return record;
        } else {
            Map<Integer, SoftReference<Record>> softMap = softCache.computeIfAbsent(clazz, k -> new HashMap<>());

            SoftReference<Record> ref = softMap.get(key);
            if (ref != null) {
                return ref.get();
            } else {
                return null;
            }
        }
    }

    public void remove(Record record) {
        Map<Integer, Record> map = cache.computeIfAbsent(record.getClass(), k -> new HashMap<>());
        Map<Integer, SoftReference<Record>> softMap = softCache.computeIfAbsent(record.getClass(), k -> new HashMap<>());

        map.remove(record.getId());
        softMap.remove(record.getId());
    }

    public void remove(Integer id, Class<? extends Record> clazz) {
        Map<Integer, Record> map = cache.computeIfAbsent(clazz, k -> new HashMap<>());
        Map<Integer, SoftReference<Record>> softMap = softCache.computeIfAbsent(clazz, k -> new HashMap<>());

        map.remove(id);
        softMap.remove(id);
    }

    public Stream<? extends Record> stream(Class<? extends Record> clazz) {
        Map<Integer, Record> map = cache.computeIfAbsent(clazz, k -> new HashMap<>());
        return map.values().stream();
    }
}
