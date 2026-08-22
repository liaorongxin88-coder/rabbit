package com.rabbit.app.modules.repro.dto;

import java.util.List;

/**
 * 待办分页结果。
 *
 * <p>带总数是为了首页角标：数量与列表来自同一组过滤条件，不会出现
 * 「角标显示 3 条、点进去只有 1 条」这类对不上的情况。
 */
public record TaskPage(long total, int page, int size, List<TaskView> items) {
}
