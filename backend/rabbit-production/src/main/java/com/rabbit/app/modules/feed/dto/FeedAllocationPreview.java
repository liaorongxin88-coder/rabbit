package com.rabbit.app.modules.feed.dto;

import java.util.List;

public record FeedAllocationPreview(List<Group> groups) {
    public record Group(Long batchId, String phase, int rabbitCount) {}
}
