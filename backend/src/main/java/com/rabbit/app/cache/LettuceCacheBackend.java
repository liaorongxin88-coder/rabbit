package com.rabbit.app.cache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

final class LettuceCacheBackend implements CacheBackend {
    private final RedisClient client;
    private final Object connectionMonitor = new Object();
    private volatile StatefulRedisConnection<String, String> connection;

    LettuceCacheBackend(CacheProperties properties) {
        RedisURI uri = buildUri(properties);
        this.client = RedisClient.create(uri);
        this.client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(properties.getConnectTimeout())
                        .build())
                .build());
    }

    @Override
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(commands().get(key));
        } catch (RedisException error) {
            resetConnection();
            throw new CacheBackendException("cache get failed", error);
        }
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        try {
            commands().set(key, value, SetArgs.Builder.px(ttl.toMillis()));
        } catch (RedisException error) {
            resetConnection();
            throw new CacheBackendException("cache put failed", error);
        }
    }

    @Override
    public void evict(String key) {
        try {
            commands().del(key);
        } catch (RedisException error) {
            resetConnection();
            throw new CacheBackendException("cache eviction failed", error);
        }
    }

    @Override
    public String evalValue(String script, List<String> keys, List<String> arguments) {
        try {
            return commands().eval(
                    script,
                    ScriptOutputType.VALUE,
                    keys.toArray(String[]::new),
                    arguments.toArray(String[]::new)
            );
        } catch (RedisException error) {
            resetConnection();
            throw new CacheBackendException("cache script failed", error);
        }
    }

    String ping() {
        try {
            return commands().ping();
        } catch (RedisException error) {
            resetConnection();
            throw new CacheBackendException("cache ping failed", error);
        }
    }

    @Override
    public void close() {
        resetConnection();
        client.shutdown();
    }

    private RedisCommands<String, String> commands() {
        StatefulRedisConnection<String, String> current = connection;
        if (current == null || !current.isOpen()) {
            synchronized (connectionMonitor) {
                current = connection;
                if (current == null || !current.isOpen()) {
                    connection = client.connect();
                    current = connection;
                }
            }
        }
        return current.sync();
    }

    private void resetConnection() {
        StatefulRedisConnection<String, String> current;
        synchronized (connectionMonitor) {
            current = connection;
            connection = null;
        }
        if (current != null) {
            current.close();
        }
    }

    static RedisURI buildUri(CacheProperties properties) {
        RedisURI.Builder builder = RedisURI.Builder.redis(properties.getHost(), properties.getPort())
                .withDatabase(properties.getDatabase())
                .withSsl(properties.isSslEnabled())
                .withTimeout(properties.getCommandTimeout());
        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username != null && !username.isBlank()) {
            builder.withAuthentication(username, password);
        } else if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
        return builder.build();
    }
}
