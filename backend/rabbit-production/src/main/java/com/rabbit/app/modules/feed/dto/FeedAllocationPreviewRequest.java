package com.rabbit.app.modules.feed.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.List;

public record FeedAllocationPreviewRequest(
    @NotEmpty(message = "rabbitIds不能为空") List<Long> rabbitIds,
    @NotNull(message = "feedTime不能为空") Date feedTime
) {}
