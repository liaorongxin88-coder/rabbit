package com.rabbit.app.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CreateHouseRequest {
    @NotBlank(message = "兔舍名称不能为空")
    @Size(max = 100, message = "兔舍名称过长")
    private String name;

    @NotNull(message = "排数不能为空")
    @Min(value = 0, message = "排数不能小于0")
    private Integer layoutRows;

    @NotNull(message = "列数不能为空")
    @Min(value = 0, message = "列数不能小于0")
    private Integer layoutCols;

    @NotNull(message = "层数不能为空")
    @Min(value = 0, message = "层数不能小于0")
    private Integer layoutLayers;

    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLayoutRows() {
        return layoutRows;
    }

    public void setLayoutRows(Integer layoutRows) {
        this.layoutRows = layoutRows;
    }

    public Integer getLayoutCols() {
        return layoutCols;
    }

    public void setLayoutCols(Integer layoutCols) {
        this.layoutCols = layoutCols;
    }

    public Integer getLayoutLayers() {
        return layoutLayers;
    }

    public void setLayoutLayers(Integer layoutLayers) {
        this.layoutLayers = layoutLayers;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
