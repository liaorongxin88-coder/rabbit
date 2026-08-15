package com.rabbit.app.modules.rabbit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;

public class CreateRabbitRequest {
    @NotNull(message = "cageId不能为空")
    private Long cageId;

    private Long motherId;

    @NotBlank(message = "type不能为空")
    private String type;

    @NotBlank(message = "gender不能为空")
    private String gender;

    @Size(max = 100, message = "品种过长")
    private String breed;

    private String arrivalMethod;
    private Date arrivalDate;
    private Double weight;

    @Size(max = 20, message = "生长阶段不合法")
    private String growthStage;

    @Size(max = 20, message = "繁殖阶段不合法")
    private String reproductiveStage;

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
}
