package com.rabbit.app.modules.nfc.dto;

public class NfcCageQueueRow {
    private Long cageId;
    private String cageNumber;
    private String genericTagUid;
    private String cageTagUid;

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public String getCageNumber() {
        return cageNumber;
    }

    public void setCageNumber(String cageNumber) {
        this.cageNumber = cageNumber;
    }

    public String getGenericTagUid() {
        return genericTagUid;
    }

    public void setGenericTagUid(String genericTagUid) {
        this.genericTagUid = genericTagUid;
    }

    public String getCageTagUid() {
        return cageTagUid;
    }

    public void setCageTagUid(String cageTagUid) {
        this.cageTagUid = cageTagUid;
    }
}
