package com.rabbit.app.modules.file.dto;

public record BusinessFileUploadResponse(
    String fileId,
    String fileName,
    String contentType,
    long sizeBytes
) {
}
