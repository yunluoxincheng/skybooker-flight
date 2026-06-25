package com.skybooker.ai.mapper;

import com.skybooker.ai.entity.AiLlmConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * ai_llm_config 单行配置表访问层。{@code findActive} 返回固定 id=1 的当前生效行，
 * 不存在时返回 {@code null}，由 provider 层 fallback 到环境变量默认值。
 */
@Mapper
public interface AiLlmConfigMapper {

    AiLlmConfig findActive();

    /**
     * 幂等写入 id=1 的单行配置：存在则覆盖，不存在则插入。
     * apiKeyCipher 由 service 层决定取新加密值或保留旧值。
     */
    void upsert(AiLlmConfig config);
}
