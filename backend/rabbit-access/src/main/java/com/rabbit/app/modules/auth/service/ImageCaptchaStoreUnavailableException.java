package com.rabbit.app.modules.auth.service;

public class ImageCaptchaStoreUnavailableException extends RuntimeException {
    public ImageCaptchaStoreUnavailableException(String message) {
        super(message);
    }

    public ImageCaptchaStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
