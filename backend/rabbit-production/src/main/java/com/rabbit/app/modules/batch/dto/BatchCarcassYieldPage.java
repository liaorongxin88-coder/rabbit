package com.rabbit.app.modules.batch.dto;

import java.util.List;

public record BatchCarcassYieldPage(
    List<BatchCarcassYieldView> items,
    long total,
    int page,
    int pageSize
) {}
