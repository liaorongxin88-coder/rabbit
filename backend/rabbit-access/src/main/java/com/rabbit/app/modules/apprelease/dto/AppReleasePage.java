package com.rabbit.app.modules.apprelease.dto;

import java.util.List;

public class AppReleasePage {
    private final List<AppReleaseItem> items;
    private final long total;
    private final int page;
    private final int pageSize;

    public AppReleasePage(List<AppReleaseItem> items, long total, int page, int pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<AppReleaseItem> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
