package com.rabbit.app.modules.appupdate;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbit.app.modules.appupdate.support.MojibakeText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MojibakeTextTest {
    /**
     * 1.0.8 线上真实数据，从 /api/app-updates/check 的响应里原样取出。
     * 真机上的更新弹窗显示的就是这一串。
     *
     * <p>注意里面有 \u0081、\u008D、\u008F 这些 CP1252 未定义的槽位，
     * Java 的 windows-1252 解码器会把它们变成 U+FFFD，所以还原逻辑不能直接
     * 用那个 charset。这条用例就是拿来钉住这一点的。
     */
    private static final String BROKEN_1_0_8 =
            "1.0.8\u00EF\u00BC\u0161\u00E6\u2013\u00B0\u00E5\u00A2\u017E\u00E5\u203A"
                    + "\u00BE\u00E7\u2030\u2021\u00E9\u00AA\u0152\u00E8\u00AF\u0081\u00E7\u00A0"
                    + "\u0081\u00E3\u20AC\u0081\u00E5\u00BC\u201A\u00E5\u00B8\u00B8\u00E8\u00AE"
                    + "\u00B0\u00E5\u00BD\u2022\u00E6\u2030\u2039\u00E5\u0160\u00A8\u00E5\u00BD"
                    + "\u2022\u00E5\u2026\u00A5\u00E3\u20AC\u0081\u00E6\u0160\u2022\u00E5\u2013"
                    + "\u201A\u00E5\u00BD\u2022\u00E5\u2026\u00A5\u00E3\u20AC\u0081\u00E6\u2030"
                    + "\u00B9\u00E9\u2021\u008F\u00E5\u2026\u00A5\u00E6\u00A0\u008F\u00E3\u20AC"
                    + "\u0081\u00E7\u00B9\u0081\u00E8\u201A\u00B2\u00E4\u00BB\u00BB\u00E5\u0160"
                    + "\u00A1\u00E4\u00B8\u017D\u00E6\u2030\u00B9\u00E6\u00AC\u00A1\u00E5\u00A5"
                    + "\u2018\u00E7\u00BA\u00A6\u00E6\u203A\u00B4\u00E6\u2013\u00B0\u00EF\u00BC"
                    + "\u0152\u00E5\u00B9\u00B6\u00E4\u00BF\u00AE\u00E5\u00A4\u008D\u00E8\u00AF"
                    + "\u00B7\u00E6\u00B1\u201A\u00E5\u00B9\u201A\u00E7\u00AD\u2030\u00E7\u00AB"
                    + "\u017E\u00E6\u20AC\u0081\u00E5\u2019\u0152\u00E5\u00BC\u201A\u00E5\u00B8"
                    + "\u00B8\u00E4\u00BF\u00A1\u00E6\u0081\u00AF\u00E6\u00B3\u201E\u00E6\u00BC"
                    + "\u008F\u00E3\u20AC\u201A";

    private static final String ORIGINAL_1_0_8 =
            "1.0.8：新增图片验证码、异常记录手动录入、投喂录入、批量入栏、"
                    + "繁育任务与批次契约更新，并修复请求幂等竞态和异常信息泄漏。";

    @Test
    @DisplayName("能还原 1.0.8 线上那条乱码的原文")
    void repairsProductionRecord() {
        assertThat(MojibakeText.looksLikeMojibake(BROKEN_1_0_8)).isTrue();
        assertThat(MojibakeText.repair(BROKEN_1_0_8)).isEqualTo(ORIGINAL_1_0_8);
    }

    @Test
    @DisplayName("正常中文不会被误判")
    void keepsHealthyChineseText() {
        String healthy = "1.0.9：修复应用更新在部分机型上无法安装的问题。";

        assertThat(MojibakeText.looksLikeMojibake(healthy)).isFalse();
        assertThat(MojibakeText.repair(healthy)).isNull();
    }

    @Test
    @DisplayName("英文和带重音的西欧文字不会被误判")
    void keepsLatinText() {
        for (String text : new String[] {
                "1.0.9: fix OTA install on Android 15.",
                "Mise à jour de sécurité",
                "Übergrößen für Käfige",
                "",
        }) {
            assertThat(MojibakeText.looksLikeMojibake(text))
                    .as("不该判定为乱码：%s", text)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("null 当作正常值放过")
    void ignoresNull() {
        assertThat(MojibakeText.looksLikeMojibake(null)).isFalse();
        assertThat(MojibakeText.repair(null)).isNull();
    }
}
