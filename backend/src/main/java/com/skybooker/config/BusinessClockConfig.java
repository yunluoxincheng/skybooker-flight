package com.skybooker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 业务时钟配置。
 *
 * <p>航班起飞时间以 DATETIME（北京时间数值）存储，下单、候补、改签、退款与过期清理等
 * 业务时间判断必须基于同一业务时区 {@code Asia/Shanghai}。若依赖容器 JVM 默认时区
 * （Docker 基础镜像默认 UTC），会出现约 8 小时的可售判断偏差：已起飞航班仍可被搜索、下单、候补与改签。
 *
 * <p>部署侧通过 {@code TZ=Asia/Shanghai} 与 {@code -Duser.timezone=Asia/Shanghai} 统一容器与 MySQL 时区；
 * 此处注入固定业务时区的 {@link Clock} 作为防御纵深，使业务判断不再依赖 JVM 默认时区，
 * 亦便于集成测试在 UTC 时钟下验证业务行为不回归（issue #139）。
 *
 * @see java.time.LocalDateTime#now(Clock)
 */
@Configuration
public class BusinessClockConfig {

    /** 业务时区：所有航班时间相关判断均按此解释。 */
    public static final String BUSINESS_ZONE_ID = "Asia/Shanghai";

    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of(BUSINESS_ZONE_ID));
    }
}
