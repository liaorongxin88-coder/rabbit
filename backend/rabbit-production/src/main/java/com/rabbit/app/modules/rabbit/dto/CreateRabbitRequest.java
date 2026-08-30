package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;

public class CreateRabbitRequest {
    @NotNull(message = "cageId不能为空")
    private Long cageId;

    @Positive(message = "motherId必须大于0")
    private Long motherId;

    // rabbits.type 与 rabbits.gender 在库里都是 varchar(1)，只靠 @NotBlank 拦不住非法值。
    // 传 "FEMALE" 这类多字符串会一路击穿到 INSERT，报 Data truncation 并把 jar 路径、
    // mapper XML 位置和完整 SQL 障础回客户端。在入口处就贴合列宽定义。
    @NotBlank(message = "type不能为空")
    @Pattern(regexp = "[012]", message = "兔只类型只能是 0 种兔、1 后备兔或 2 商品兔")
    private String type;

    @NotBlank(message = "gender不能为空")
    @Pattern(regexp = "[01]", message = "兔只性别只能是 0 母或 1 公")
    private String gender;

    @Size(max = 100, message = "品种过长")
    private String breed;

    @Pattern(regexp = "[01]", message = "来源方式只能是 0 购入或 1 自留")
    private String arrivalMethod;

    @Size(max = 120, message = "供应方过长")
    private String sourceSeller;

    private Date arrivalDate;
    private Double weight;

    @Size(max = 20, message = "生长阶段不合法")
    private String growthStage;

    /** 进入当前成长阶段的日期；旧客户端缺省时由服务端回退到入场日期。 */
    private Date growthStageEnteredAt;

    @Size(max = 20, message = "繁殖阶段不合法")
    private String reproductiveStage;

    /**
     * 种母兔录入时直接指定的生产阶段（待催情/待配种/待摸胎……）。
     *
     * <p>存栏母兔很少刚好处于“什么都没发生”的起点，不能要求用户先录入再从头跑一轮。
     * 指定后后端会在同一事务里开周期并生成首个待办，取代旧的 reproductiveStage 手写。
     */
    private String reproStage;

    /** 指定生产阶段入轨时使用的生产批次。 */
    private Long batchId;

    /** 进入该阶段的日期，缺省为录入时间；它决定首个待办何时到期。 */
    private Date stageEnteredAt;

    /** 指定待摸胎/待备产/待分娩等阶段时需要的历史事实。 */
    private Date matingDate;

    private Date birthDate;

    private Integer totalKits;

    private Integer liveKits;

    private Integer keptKits;

    private Long maleRabbitId;

    private String matingMethod;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Long getCageId() {
        return cageId;
    }

    public void setCageId(Long cageId) {
        this.cageId = cageId;
    }

    public Long getMotherId() {
        return motherId;
    }

    public void setMotherId(Long motherId) {
        this.motherId = motherId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getArrivalMethod() {
        return arrivalMethod;
    }

    public void setArrivalMethod(String arrivalMethod) {
        this.arrivalMethod = arrivalMethod;
    }

    public String getSourceSeller() {
        return sourceSeller;
    }

    public void setSourceSeller(String sourceSeller) {
        this.sourceSeller = sourceSeller;
    }

    public Date getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(Date arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public String getGrowthStage() {
        return growthStage;
    }

    public void setGrowthStage(String growthStage) {
        this.growthStage = growthStage;
    }

    public Date getGrowthStageEnteredAt() {
        return growthStageEnteredAt;
    }

    public void setGrowthStageEnteredAt(Date growthStageEnteredAt) {
        this.growthStageEnteredAt = growthStageEnteredAt;
    }

    public String getReproductiveStage() {
        return reproductiveStage;
    }

    public void setReproductiveStage(String reproductiveStage) {
        this.reproductiveStage = reproductiveStage;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getReproStage() { return reproStage; }

    public void setReproStage(String reproStage) { this.reproStage = reproStage; }

    public Long getBatchId() { return batchId; }

    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Date getStageEnteredAt() { return stageEnteredAt; }

    public void setStageEnteredAt(Date stageEnteredAt) { this.stageEnteredAt = stageEnteredAt; }

    public Date getMatingDate() { return matingDate; }

    public void setMatingDate(Date matingDate) { this.matingDate = matingDate; }

    public Date getBirthDate() { return birthDate; }

    public void setBirthDate(Date birthDate) { this.birthDate = birthDate; }

    public Integer getTotalKits() { return totalKits; }

    public void setTotalKits(Integer totalKits) { this.totalKits = totalKits; }

    public Integer getLiveKits() { return liveKits; }

    public void setLiveKits(Integer liveKits) { this.liveKits = liveKits; }

    public Integer getKeptKits() { return keptKits; }

    public void setKeptKits(Integer keptKits) { this.keptKits = keptKits; }

    public Long getMaleRabbitId() { return maleRabbitId; }

    public void setMaleRabbitId(Long maleRabbitId) { this.maleRabbitId = maleRabbitId; }

    public String getMatingMethod() { return matingMethod; }

    public void setMatingMethod(String matingMethod) { this.matingMethod = matingMethod; }
}
