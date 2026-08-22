package com.rabbit.app.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class CreateAdminFarmRequest {
    @NotBlank(message = "兔场名称不能为空")
    @Size(max = 100, message = "兔场名称不能超过100个字符")
    private String name;

    @NotNull(message = "排数不能为空")
    @Min(value = 1, message = "排数必须大于0")
    @Max(value = 100, message = "排数不能超过100")
    private Integer layoutRows;

    @NotNull(message = "列数不能为空")
    @Min(value = 1, message = "列数必须大于0")
    @Max(value = 100, message = "列数不能超过100")
    private Integer layoutCols;

    @NotNull(message = "层数不能为空")
    @Min(value = 1, message = "层数必须大于0")
    @Max(value = 100, message = "层数不能超过100")
    private Integer layoutLayers;

    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;

    @Positive(message = "ownerUserId不合法")
    private Long ownerUserId;

    @Size(max = 32, message = "所有者手机号不能超过32个字符")
    private String ownerPhone;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "requestId不合法")
    private String requestId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getLayoutRows() { return layoutRows; }
    public void setLayoutRows(Integer layoutRows) { this.layoutRows = layoutRows; }
    public Integer getLayoutCols() { return layoutCols; }
    public void setLayoutCols(Integer layoutCols) { this.layoutCols = layoutCols; }
    public Integer getLayoutLayers() { return layoutLayers; }
    public void setLayoutLayers(Integer layoutLayers) { this.layoutLayers = layoutLayers; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
