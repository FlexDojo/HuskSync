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

package net.william278.husksync.redis;

import de.themoep.minedown.adventure.MineDown;
import io.lettuce.core.RedisClient;
import net.william278.husksync.HuskSync;
import net.william278.husksync.data.UserData;
import net.william278.husksync.player.User;
import net.william278.husksync.redis.redisdata.RedisPubSub;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the connection to the Redis server, handling the caching of user data
 */
public class RedisManager {

    protected static final String KEY_NAMESPACE = "husksync:";
    protected static String clusterId = "";
    private final HuskSync plugin;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final boolean redisUseSsl;
    private RedisImpl redisImpl;

    public RedisManager(@NotNull HuskSync plugin) {
        this.plugin = plugin;
        clusterId = plugin.getSettings().getClusterId();

        // Set redis credentials
        this.redisHost = plugin.getSettings().getRedisHost();
        this.redisPort = plugin.getSettings().getRedisPort();
        this.redisPassword = plugin.getSettings().getRedisPassword();
        this.redisUseSsl = plugin.getSettings().isRedisUseSsl();
    }

    /**
     * Initialize the redis connection pool
     *
     * @return a future returning void when complete
     */
    public boolean initialize() {
        RedisClient client;
        if (redisPassword.isBlank()) {
            client = RedisClient.create("redis://" + redisHost + ":" + redisPort);
        } else {
            client = RedisClient.create("redis://" + redisPassword + "@" + redisHost + ":" + redisPort);
        }
        redisImpl = new RedisImpl(client);
        CompletableFuture.runAsync(this::subscribe);
        return true;
    }

    private void subscribe() {

        redisImpl.getPubSubConnection(connection -> {

            connection.addListener(new RedisPubSub<>() {
                @Override
                public void message(String channel, String message) {
                    final RedisMessageType messageType = RedisMessageType.getTypeFromChannel(channel).orElse(null);
                    if (messageType != RedisMessageType.UPDATE_USER_DATA) {
                        return;
                    }

                    final RedisMessage redisMessage = RedisMessage.fromJson(message);
                    plugin.getOnlineUser(redisMessage.targetUserUuid).ifPresent(user -> {
                        final UserData userData = plugin.getDataAdapter().fromBytes(redisMessage.data);
                        user.setData(userData, plugin).thenAccept(succeeded -> {
                            if (succeeded) {
                                switch (plugin.getSettings().getNotificationDisplaySlot()) {
                                    case CHAT -> plugin.getLocales().getLocale("data_update_complete")
                                            .ifPresent(user::sendMessage);
                                    case ACTION_BAR -> plugin.getLocales().getLocale("data_update_complete")
                                            .ifPresent(user::sendActionBar);
                                    case TOAST -> plugin.getLocales().getLocale("data_update_complete")
                                            .ifPresent(locale -> user.sendToast(locale, new MineDown(""),
                                                    "minecraft:bell", "TASK"));
                                }
                                plugin.getEventCannon().fireSyncCompleteEvent(user);
                            } else {
                                plugin.getLocales().getLocale("data_update_failed")
                                        .ifPresent(user::sendMessage);
                            }
                        });
                    });
                }
            });


            connection.async().subscribe(Arrays.stream(RedisMessageType.values())
                    .map(RedisMessageType::getMessageChannel)
                    .toArray(String[]::new));


        });
    }


    protected void sendMessage(@NotNull String channel, @NotNull String message) {
        redisImpl.getConnectionAsync(connection -> connection.publish(channel, message));
    }

    public CompletableFuture<Void> sendUserDataUpdate(@NotNull User user, @NotNull UserData userData) {
        return CompletableFuture.runAsync(() -> {
            final RedisMessage redisMessage = new RedisMessage(user.uuid, plugin.getDataAdapter().toBytes(userData));
            redisMessage.dispatch(this, RedisMessageType.UPDATE_USER_DATA);
        });
    }

    /**
     * Set a user's data to the Redis server
     *
     * @param user     the user to set data for
     * @param userData the user's data to set
     * @return a future returning void when complete
     */
    public CompletableFuture<Void> setUserData(@NotNull User user, @NotNull UserData userData) {
        try {
            return CompletableFuture.runAsync(() -> {

                // Set the user's data as a compressed byte array of the json using Snappy

                redisImpl.getBinaryConnectionAsync(connection ->
                        connection.setex(RedisKeyType.DATA_UPDATE.getKeyPrefix() + ":" + user.uuid,
                                RedisKeyType.DATA_UPDATE.timeToLive,
                                plugin.getDataAdapter().toBytes(userData)));

                plugin.debug("[" + user.username + "] Set " + RedisKeyType.DATA_UPDATE.name()
                        + " key to redis at: " +
                        new SimpleDateFormat("mm:ss.SSS").format(new Date()));

            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CompletableFuture<Void> setUserServerSwitch(@NotNull User user) {
        return CompletableFuture.runAsync(() -> {

            redisImpl.getBinaryConnectionAsync(connection ->
                    connection.setex(RedisKeyType.SERVER_SWITCH.getKeyPrefix() + ":" + user.uuid,
                            RedisKeyType.SERVER_SWITCH.timeToLive, new byte[0]));

            plugin.debug("[" + user.username + "] Set " + RedisKeyType.SERVER_SWITCH.name()
                    + " key to redis at: " +
                    new SimpleDateFormat("mm:ss.SSS").format(new Date()));
        });
    }

    /**
     * Fetch a user's data from the Redis server and consume the key if found
     *
     * @param user The user to fetch data for
     * @return The user's data, if it's present on the database. Otherwise, an empty optional.
     */
    public CompletableFuture<Optional<UserData>> getUserData(@NotNull User user) {

        CompletableFuture<Optional<UserData>> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() ->
                redisImpl.getBinaryConnection(connection -> {
                    final String key = RedisKeyType.DATA_UPDATE.getKeyPrefix() + ":" + user.uuid;

                    return connection.get(key)
                            .thenApply(bytes -> ((byte[]) bytes))
                            .thenAccept(dataByteArray -> {

                                if (dataByteArray == null) {
                                    plugin.debug("[" + user.username + "] Could not read " +
                                            RedisKeyType.DATA_UPDATE.name() + " key from redis at: " +
                                            new SimpleDateFormat("mm:ss.SSS").format(new Date()));
                                    future.complete(Optional.empty());
                                    return;
                                }
                                plugin.debug("[" + user.username + "] Successfully read "
                                        + RedisKeyType.DATA_UPDATE.name() + " key from redis at: " +
                                        new SimpleDateFormat("mm:ss.SSS").format(new Date()));

                                // Use the data adapter to convert the byte array to a UserData object

                                final UserData userData = plugin.getDataAdapter().fromBytes(dataByteArray);
                                future.complete(Optional.of(userData));

                                // Consume the key (delete from redis)
                                connection.del(key);
                            });
                }));

        return future;
    }

    public CompletableFuture<Boolean> getUserServerSwitch(@NotNull User user) {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        CompletableFuture.runAsync(() ->
                redisImpl.getBinaryConnection(connection -> {
                    final String key = getKey(RedisKeyType.SERVER_SWITCH, user.uuid);

                    return connection.get(key)
                            .thenApply(bytes -> ((byte[]) bytes))
                            .thenAccept(dataByteArray -> {

                                if (dataByteArray == null) {
                                    plugin.debug("[" + user.username + "] Could not read " +
                                            RedisKeyType.SERVER_SWITCH.name() + " key from redis at: " +
                                            new SimpleDateFormat("mm:ss.SSS").format(new Date()));
                                    future.complete(false);
                                    return;
                                }
                                plugin.debug("[" + user.username + "] Successfully read "
                                        + RedisKeyType.SERVER_SWITCH.name() + " key from redis at: " +
                                        new SimpleDateFormat("mm:ss.SSS").format(new Date()));

                                future.complete(true);

                                // Consume the key (delete from redis)
                                connection.del(key);
                            });
                }));

        return future;
    }

    public void close() {
        redisImpl.close();
    }

    private static String getKey(@NotNull RedisKeyType keyType, @NotNull UUID uuid) {
        return (keyType.getKeyPrefix() + ":" + uuid);
    }

}
