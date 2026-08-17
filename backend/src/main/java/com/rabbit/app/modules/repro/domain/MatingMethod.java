package com.rabbit.app.modules.repro.domain;

import com.rabbit.app.common.BizException;
import java.util.Locale;

/** 配种方式（设计 §5.2 配种表单必填）。 */
public enum MatingMethod {
    NATURAL("体配"),
    AI("人工授精");

    private final String label;

    MatingMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 解析配种方式；<b>缺省返回 null 而不是报错</b>。
     *
     * <p>是否必填由动作决定，判断权在状态机的 validateFacts，不在解析器：
     * 开启周期、催情、摸胎都不涉及配种方式，若在此处强制非空，等于要求每个动作
     * 都捎带一个无关字段。
     *
     * <p>更硬的约束来自 P4：旧端点要适配到新服务上，而无 OTA 的老 APK 从不发送
     * 这个字段——一旦必填，老客户端的配种提交会全部变成 400。
     */
    public static MatingMethod parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MatingMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "配种方式不合法: " + value);
        }
    }
}
