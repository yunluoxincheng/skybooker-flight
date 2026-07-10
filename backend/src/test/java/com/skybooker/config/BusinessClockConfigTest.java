package com.skybooker.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证业务时钟 {@link BusinessClockConfig} 固定为 Asia/Shanghai，且与 JVM 默认时区解耦。
 *
 * <p>issue #139：容器 JVM 默认 UTC 时，裸 {@code LocalDateTime.now()} 会比业务时间慢约 8 小时，
 * 导致已起飞航班仍可购买。业务时钟必须不依赖 JVM 默认时区。
 */
class BusinessClockConfigTest {

    @Test
    void businessClock_zoneIsAsiaShanghai() {
        var clock = new BusinessClockConfig().businessClock();
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of(BusinessClockConfig.BUSINESS_ZONE_ID));
    }

    @Test
    void businessClock_progressesInShanghaiEvenUnderUtcJvm() {
        TimeZone original = TimeZone.getDefault();
        try {
            // 模拟 issue #139 的 UTC 容器
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            var clock = new BusinessClockConfig().businessClock();
            // Clock 固定 Asia/Shanghai，不受 JVM 默认时区影响
            assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));

            // 在 UTC JVM 下，now(clock) 仍按上海时间（UTC+8）推进，比 UTC now 快约 8 小时
            LocalDateTime shanghaiNow = LocalDateTime.now(clock);
            LocalDateTime utcNow = LocalDateTime.now(ZoneId.of("UTC"));
            assertThat(shanghaiNow).isAfter(utcNow);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
