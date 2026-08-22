package com.rabbit.app.cache;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    @NotNull
    private CacheProvider provider = CacheProvider.NONE;

    @NotBlank
    private String host = "127.0.0.1";

    @Min(1)
    @Max(65535)
    private int port = 6379;

    private String username = "";
    private String password = "";

    @Min(0)
    private int database = 0;

    private boolean sslEnabled;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration commandTimeout = Duration.ofSeconds(1);

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9:._-]{0,127}$")
    private String keyPrefix = "rabbit:cache:v1";

    public CacheProvider getProvider() {
        return provider;
    }

    public void setProvider(CacheProvider provider) {
        this.provider = provider;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getCommandTimeout() {
        return commandTimeout;
    }

    public void setCommandTimeout(Duration commandTimeout) {
        this.commandTimeout = commandTimeout;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    @AssertTrue(message = "cache timeouts must be positive")
    public boolean isTimeoutConfigurationValid() {
        return connectTimeout != null && !connectTimeout.isZero() && !connectTimeout.isNegative()
                && commandTimeout != null && !commandTimeout.isZero() && !commandTimeout.isNegative();
    }

    @AssertTrue(message = "cache username requires a password")
    public boolean isAuthenticationConfigurationValid() {
        return username == null || username.isBlank() || (password != null && !password.isBlank());
    }
}
