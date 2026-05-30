package com.skybooker.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.skybooker.**.mapper")
public class MyBatisConfig {
}
