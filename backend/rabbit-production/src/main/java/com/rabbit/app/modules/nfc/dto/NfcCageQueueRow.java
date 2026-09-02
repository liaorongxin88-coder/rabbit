package com.rabbit.app.modules.nfc.dto;

public class NfcCageQueueRow {
    private Long cageId;
    private String cageNumber;
    private String rowCode;
    private Integer layerIndex;
    private Integer positionIndex;
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

    public String getRowCode() {
        return rowCode;
    }

    public void setRowCode(String rowCode) {
        this.rowCode = rowCode;
    }

    public Integer getLayerIndex() {
        return layerIndex;
    }

    public void setLayerIndex(Integer layerIndex) {
        this.layerIndex = layerIndex;
    }

    public Integer getPositionIndex() {
        return positionIndex;
    }

    public void setPositionIndex(Integer positionIndex) {
        this.positionIndex = positionIndex;
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
