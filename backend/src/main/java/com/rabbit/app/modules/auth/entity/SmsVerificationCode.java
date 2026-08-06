package com.rabbit.app.modules.auth.entity;

import java.util.Date;

public class SmsVerificationCode {
    private Long id;
    private String phoneHash;
    private String purpose;
    private String codeHash;
    private String requestIp;
    private String status;
    private Integer attemptCount;
    private Long sendBucket;
    private Date expiresTime;
    private Date consumedTime;
    private Date createTime;
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getRequestIp() {
        return requestIp;
    }

    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Long getSendBucket() {
        return sendBucket;
    }

    public void setSendBucket(Long sendBucket) {
        this.sendBucket = sendBucket;
    }

    public Date getExpiresTime() {
        return expiresTime;
    }

    public void setExpiresTime(Date expiresTime) {
        this.expiresTime = expiresTime;
    }

    public Date getConsumedTime() {
        return consumedTime;
    }

    public void setConsumedTime(Date consumedTime) {
        this.consumedTime = consumedTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
