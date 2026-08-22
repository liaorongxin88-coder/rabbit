package com.rabbit.app.modules.cage.dto;

public class CageCountRow {
    private Long cageId;
    private Integer rabbitCount;

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Integer getRabbitCount() {
        return rabbitCount;
    }

    public void setRabbitCount(Integer rabbitCount) {
        this.rabbitCount = rabbitCount;
    }
}
