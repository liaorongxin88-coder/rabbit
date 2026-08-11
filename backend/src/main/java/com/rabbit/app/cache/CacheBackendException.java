package com.rabbit.app.cache;

final class CacheBackendException extends RuntimeException {
    CacheBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
