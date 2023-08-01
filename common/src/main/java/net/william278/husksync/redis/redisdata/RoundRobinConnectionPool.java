/*
 * This file is part of HuskSync, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.husksync.redis.redisdata;


import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RoundRobinConnectionPool<K,V> {
    private final AtomicInteger next = new AtomicInteger(0);
    private StatefulRedisConnection<K,V>[] elements;
    private final Supplier<StatefulRedisConnection<K,V>> statefulRedisConnectionSupplier;

    public RoundRobinConnectionPool(Supplier<StatefulRedisConnection<K,V>> statefulRedisConnectionSupplier, int poolSize) {
        this.statefulRedisConnectionSupplier = statefulRedisConnectionSupplier;
        this.elements = new StatefulRedisConnection[poolSize];
        for(int i = 0; i < poolSize; i++) {
            elements[i] = statefulRedisConnectionSupplier.get();
        }
    }
    public void expandPool(int expandBy) {
        if(expandBy <= 0)
            throw new IllegalArgumentException("expandBy must be greater than 0");
        this.elements = Arrays.copyOf(elements, elements.length + expandBy);
    }

    public StatefulRedisConnection<K, V> get() {
        int index = next.getAndIncrement() % elements.length;
        StatefulRedisConnection<K, V> connection = elements[index];
        if (connection != null)
            if (connection.isOpen())
                return connection;

        connection = statefulRedisConnectionSupplier.get();
        elements[index] = connection;
        return connection;
    }

}