package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;

/**
 * 按排、位、层闭区间批量录入同一批兔只。
 *
 * <p>范围只接受数值坐标。缺坐标和 LEGACY 排号的笼位无法落在该空间范围中，
 * 客户端会将它们保留在未编排区域，要求先补齐坐标。</p>
 */
public class RangeRabbitEntryRequest {
    @NotNull(message = "起始排不能为空")
    @Min(value = 1, message = "起始排必须大于 0")
    private Integer rowStart;

    @NotNull(message = "结束排不能为空")
    @Min(value = 1, message = "结束排必须大于 0")
    private Integer rowEnd;

    @NotNull(message = "起始位不能为空")
    @Min(value = 1, message = "起始位必须大于 0")
    private Integer positionStart;

    @NotNull(message = "结束位不能为空")
    @Min(value = 1, message = "结束位必须大于 0")
    private Integer positionEnd;

    @NotNull(message = "起始层不能为空")
    @Min(value = 1, message = "起始层必须大于 0")
    private Integer layerStart;

    @NotNull(message = "结束层不能为空")
    @Min(value = 1, message = "结束层必须大于 0")
    private Integer layerEnd;

    @NotNull(message = "每笼数量不能为空")
    @Min(value = 1, message = "每笼数量至少为 1")
    @Max(value = 10, message = "每笼数量不能超过 10")
    private Integer rabbitsPerCage;

    private Long motherId;

    // rabbits.type 与 rabbits.gender 在库里都是 varchar(1)，只靠 @NotBlank 拦不住非法值。
    // 传 "FEMALE" 这类多字符串会一路击穿到 INSERT，报 Data truncation 并把 jar 路径、
    // mapper XML 位置和完整 SQL 障础回客户端。批量入笼下这个洞更痛：
    // 一次请求最多 1000 只，错误值会在批量 INSERT 中途才爆。
    @NotBlank(message = "type不能为空")
    @Pattern(regexp = "[012]", message = "兔只类型只能是 0 种兔、1 后备兔或 2 商品兔")
    private String type;

    @NotBlank(message = "gender不能为空")
    @Pattern(regexp = "[01]", message = "兔只性别只能是 0 母或 1 公")
    private String gender;

    @Size(max = 100, message = "品种过长")
    private String breed;

    private String arrivalMethod;
    private Date arrivalDate;
    private Double weight;

    @Size(max = 20, message = "生长阶段不合法")
    private String growthStage;

    private Date growthStageEnteredAt;

    @Size(max = 20, message = "繁殖阶段不合法")
    private String reproductiveStage;

    private String reproStage;
    private Long batchId;
    private Date stageEnteredAt;
    private Date matingDate;
    private Date birthDate;
    private Integer liveKits;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public Integer getRowStart() { return rowStart; }
    public void setRowStart(Integer rowStart) { this.rowStart = rowStart; }
    public Integer getRowEnd() { return rowEnd; }
    public void setRowEnd(Integer rowEnd) { this.rowEnd = rowEnd; }
    public Integer getPositionStart() { return positionStart; }
    public void setPositionStart(Integer positionStart) { this.positionStart = positionStart; }
    public Integer getPositionEnd() { return positionEnd; }
    public void setPositionEnd(Integer positionEnd) { this.positionEnd = positionEnd; }
    public Integer getLayerStart() { return layerStart; }
    public void setLayerStart(Integer layerStart) { this.layerStart = layerStart; }
    public Integer getLayerEnd() { return layerEnd; }
    public void setLayerEnd(Integer layerEnd) { this.layerEnd = layerEnd; }
    public Integer getRabbitsPerCage() { return rabbitsPerCage; }
    public void setRabbitsPerCage(Integer rabbitsPerCage) { this.rabbitsPerCage = rabbitsPerCage; }
    public Long getMotherId() { return motherId; }
    public void setMotherId(Long motherId) { this.motherId = motherId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public String getArrivalMethod() { return arrivalMethod; }
    public void setArrivalMethod(String arrivalMethod) { this.arrivalMethod = arrivalMethod; }
    public Date getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(Date arrivalDate) { this.arrivalDate = arrivalDate; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public String getGrowthStage() { return growthStage; }
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }
    public Date getGrowthStageEnteredAt() { return growthStageEnteredAt; }
    public void setGrowthStageEnteredAt(Date growthStageEnteredAt) { this.growthStageEnteredAt = growthStageEnteredAt; }
    public String getReproductiveStage() { return reproductiveStage; }
    public void setReproductiveStage(String reproductiveStage) { this.reproductiveStage = reproductiveStage; }
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
    public Integer getLiveKits() { return liveKits; }
    public void setLiveKits(Integer liveKits) { this.liveKits = liveKits; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
