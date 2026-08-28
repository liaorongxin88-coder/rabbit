package com.rabbit.app.modules.rabbit.entity;

import com.rabbit.app.common.Stamped;
import java.util.Date;

public class RabbitCageTransferRecord implements Stamped {
    private Long id;
    private Long houseId;
    private Long rabbitId;
    private Long fromCageId;
    private Long toCageId;
    private String transferType;
    private String requestId;
    private String createBy;
    private String operatorName;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHouseId() { return houseId; }
    public void setHouseId(Long houseId) { this.houseId = houseId; }
    public Long getRabbitId() { return rabbitId; }
    public void setRabbitId(Long rabbitId) { this.rabbitId = rabbitId; }
    public Long getFromCageId() { return fromCageId; }
    public void setFromCageId(Long fromCageId) { this.fromCageId = fromCageId; }
    public Long getToCageId() { return toCageId; }
    public void setToCageId(Long toCageId) { this.toCageId = toCageId; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return null; }
    public void setUpdateBy(String updateBy) { }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
