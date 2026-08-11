package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateAdminFarmRequest {
    @NotBlank(message = "兔场名称不能为空")
    @Size(max = 100, message = "兔场名称不能超过100个字符")
    private String name;

    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
