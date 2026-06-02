package com.rabbit.app.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class UpdateHouseRequest {
    @NotBlank(message = "兔舍名称不能为空")
    @Size(max = 100, message = "兔舍名称过长")
    private String name;

    private String remark;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
