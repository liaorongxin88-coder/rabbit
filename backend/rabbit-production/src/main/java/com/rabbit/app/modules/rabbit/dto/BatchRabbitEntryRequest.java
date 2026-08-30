package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Date;

/** A same-cage intake of commodity rabbits measured as one lot. */
public class BatchRabbitEntryRequest {
    @NotNull(message = "cageId不能为空")
    @Positive(message = "cageId必须大于0")
    private Long cageId;

    @Positive(message = "motherId必须大于0")
    private Long motherId;

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

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 10, message = "数量不能超过 10")
    private Integer quantity;

    @NotNull(message = "总重量不能为空")
    @DecimalMin(value = "0.01", message = "总重量必须大于 0")
    @DecimalMax(value = "100.00", message = "总重量不能超过 100 kg")
    private Double totalWeight;

    @Size(max = 20, message = "生长阶段不合法")
    private String growthStage;

    private Date growthStageEnteredAt;

    @Size(max = 20, message = "繁殖阶段不合法")
    private String reproductiveStage;

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId过长")
    private String requestId;

    public Long getCageId() { return cageId; }
    public void setCageId(Long cageId) { this.cageId = cageId; }
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
    public String getSourceSeller() { return sourceSeller; }
    public void setSourceSeller(String sourceSeller) { this.sourceSeller = sourceSeller; }
    public Date getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(Date arrivalDate) { this.arrivalDate = arrivalDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getTotalWeight() { return totalWeight; }
    public void setTotalWeight(Double totalWeight) { this.totalWeight = totalWeight; }
    public String getGrowthStage() { return growthStage; }
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }
    public Date getGrowthStageEnteredAt() { return growthStageEnteredAt; }
    public void setGrowthStageEnteredAt(Date growthStageEnteredAt) { this.growthStageEnteredAt = growthStageEnteredAt; }
    public String getReproductiveStage() { return reproductiveStage; }
    public void setReproductiveStage(String reproductiveStage) { this.reproductiveStage = reproductiveStage; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
