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

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.concurrent.*;
import java.util.function.Function;


public abstract class RedisAbstract {

    protected static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final RoundRobinConnectionPool<String, Object> roundRobinConnectionPool;
    private final RoundRobinConnectionPool<String, String> roundRobinConnectionPoolString;
    protected RedisClient lettuceRedisClient;

    public RedisAbstract(RedisClient lettuceRedisClient) {
        this.lettuceRedisClient = lettuceRedisClient;
        this.roundRobinConnectionPool = new RoundRobinConnectionPool<>(() -> lettuceRedisClient.connect(new SerializedObjectCodec()), 5);
        this.roundRobinConnectionPoolString = new RoundRobinConnectionPool<>(lettuceRedisClient::connect, 2);
    }

    public <T> ScheduledFuture<T> scheduleConnection(Function<StatefulRedisConnection<String, Object>, T> function, int timeout, TimeUnit timeUnit) {
        return executorService.schedule(() -> function.apply(roundRobinConnectionPool.get()), timeout, timeUnit);
    }

    public <T> CompletionStage<T> getBinaryConnectionAsync(Function<RedisAsyncCommands<String, Object>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().async());
    }

    public <T> CompletionStage<T> getBinaryConnection(Function<RedisAsyncCommands<String, Object>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPool.get().async());
    }

    public <T> CompletionStage<T> getConnectionAsync(Function<RedisAsyncCommands<String, String>, CompletionStage<T>> redisCallBack) {
        return redisCallBack.apply(roundRobinConnectionPoolString.get().async());
    }

    //Get pubsub
    public StatefulRedisPubSubConnection<String, Object> getBinaryPubSubConnection() {
        return lettuceRedisClient.connectPubSub(new SerializedObjectCodec());
    }
    public void getBinaryPubSubConnection(RedisCallBack.PubSub.Binary redisCallBack) {
        redisCallBack.useConnection(lettuceRedisClient.connectPubSub(new SerializedObjectCodec()));
    }

    public void getPubSubConnection(RedisCallBack.PubSub redisCallBack) {
        redisCallBack.useConnection(lettuceRedisClient.connectPubSub());
    }

    public StatefulRedisConnection<String, String> getUnclosedConnection() {
        return lettuceRedisClient.connect();
    }

    public boolean isConnected() {
        try (StatefulRedisConnection<String, String> connection = lettuceRedisClient.connect()){
            return connection.isOpen();
        } catch (Exception e) {
            return false;
        }
    }
    public void close() {
        lettuceRedisClient.shutdownAsync();
    }

}
