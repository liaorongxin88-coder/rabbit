package com.rabbit.app.modules.setting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ReminderPreferenceRequest {
    private Boolean enabled = true;

    @Min(value = 0, message = "提醒提前天数不能小于0")
    @Max(value = 30, message = "提醒提前天数不能大于30")
    private Integer advanceDays = 0;

    private Boolean notifyOverdue = true;
    private List<@NotBlank(message = "提醒类型不能为空") String> taskTypes = List.of("ALL");

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getAdvanceDays() {
        return advanceDays;
    }

    public void setAdvanceDays(Integer advanceDays) {
        this.advanceDays = advanceDays;
    }

    public Boolean getNotifyOverdue() {
        return notifyOverdue;
    }

    public void setNotifyOverdue(Boolean notifyOverdue) {
        this.notifyOverdue = notifyOverdue;
    }

    public List<String> getTaskTypes() {
        return taskTypes;
    }

    public void setTaskTypes(List<String> taskTypes) {
        this.taskTypes = taskTypes;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
