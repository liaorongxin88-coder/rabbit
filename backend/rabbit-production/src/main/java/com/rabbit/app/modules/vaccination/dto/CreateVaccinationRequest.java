package com.rabbit.app.modules.vaccination.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * 接种一律走批量形状：单只兔就是长度为 1 的 rabbitIds。
 *
 * <p>不再单开一个单只端点——现场打针本来就是端着一瓶疫苗顺着笼子打过去，
 * 两套入口只会让幂等和校验逻辑各写一遍。
 */
public class CreateVaccinationRequest {
    /** 上限 500 与既有批量端点保持一致（BatchService.BULK_WRITE_SIZE）。 */
    @NotEmpty(message = "rabbitIds不能为空")
    @Size(max = 500, message = "单次接种不能超过500只兔")
    private List<Long> rabbitIds;

    @NotBlank(message = "vaccineName不能为空")
    @Size(max = 100, message = "疫苗名称不能超过100字")
    private String vaccineName;

    @Size(max = 64, message = "疫苗批号不能超过64字")
    private String vaccineBatchNo;

    @Size(max = 50, message = "剂量不能超过50字")
    private String dose;

    @Size(max = 20, message = "接种途径不能超过20字")
    private String route;

    @NotNull(message = "vaccinatedAt不能为空")
    private Date vaccinatedAt;

    private Date nextDueDate;

    private String remark;

    @NotBlank(message = "requestId不能为空")
    private String requestId;

    public List<Long> getRabbitIds() {
        return rabbitIds;
    }

    public void setRabbitIds(List<Long> rabbitIds) {
        this.rabbitIds = rabbitIds;
    }

    public String getVaccineName() {
        return vaccineName;
    }

    public void setVaccineName(String vaccineName) {
        this.vaccineName = vaccineName;
    }

    public String getVaccineBatchNo() {
        return vaccineBatchNo;
    }

    public void setVaccineBatchNo(String vaccineBatchNo) {
        this.vaccineBatchNo = vaccineBatchNo;
    }

    public String getDose() {
        return dose;
    }

    public void setDose(String dose) {
        this.dose = dose;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public Date getVaccinatedAt() {
        return vaccinatedAt;
    }

    public void setVaccinatedAt(Date vaccinatedAt) {
        this.vaccinatedAt = vaccinatedAt;
    }

    public Date getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(Date nextDueDate) {
        this.nextDueDate = nextDueDate;
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
