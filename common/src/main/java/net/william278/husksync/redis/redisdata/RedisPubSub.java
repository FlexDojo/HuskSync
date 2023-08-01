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

import io.lettuce.core.pubsub.RedisPubSubListener;

public abstract class RedisPubSub<K,V> implements RedisPubSubListener<K,V> {
    public abstract void message(K channel, V message);

    /**
     * Message received from a pattern subscription.
     *
     * @param pattern Pattern
     * @param channel Channel
     * @param message Message
     */
    public void message(K pattern, K channel, V message){

    }

    /**
     * Subscribed to a channel.
     *
     * @param channel Channel
     * @param count Subscription count.
     */
    public void subscribed(K channel, long count){

    }

    /**
     * Subscribed to a pattern.
     *
     * @param pattern Pattern.
     * @param count Subscription count.
     */
    public void psubscribed(K pattern, long count){

    }

    /**
     * Unsubscribed from a channel.
     *
     * @param channel Channel
     * @param count Subscription count.
     */
    public void unsubscribed(K channel, long count){

    }

    /**
     * Unsubscribed from a pattern.
     *
     * @param pattern Channel
     * @param count Subscription count.
     */
    public void punsubscribed(K pattern, long count){

    }
}
