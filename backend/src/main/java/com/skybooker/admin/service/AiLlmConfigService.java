package com.skybooker.admin.service;

import com.skybooker.admin.dto.AiLlmConfigDTO;
import com.skybooker.admin.entity.AdminUser;
import com.skybooker.admin.mapper.AdminMapper;
import com.skybooker.admin.vo.AiLlmConfigVO;
import com.skybooker.ai.config.DynamicLlmConfigProvider;
import com.skybooker.ai.config.LlmConfigCrypto;
import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.entity.AiLlmConfig;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import com.skybooker.common.exception.BusinessException;
import com.skybooker.common.exception.ErrorCode;
import com.skybooker.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员查看（脱敏）/ 更新 AI LLM 运行时配置。
 *
 * <p>读取走 {@link DynamicLlmConfigProvider}（DB 优先 + 环境变量 fallback + TTL 缓存）；
 * 写入加密入库、清缓存使下次请求生效、记审计（updatedBy + INFO 日志，不记 key 内容）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiLlmConfigService {

    private static final String MASK = "****";
    private static final int DEFAULT_TIMEOUT_MS = 8000;
    private static final int DEFAULT_MAX_RETRIES = 1;

    private final AiLlmConfigMapper configMapper;
    private final DynamicLlmConfigProvider configProvider;
    private final LlmConfigCrypto crypto;
    private final AdminMapper adminMapper;

    public AiLlmConfigVO getConfig() {
        LlmEffectiveConfig cfg = configProvider.getConfig();
        AiLlmConfig row = configMapper.findActive();
        Long updatedBy = row == null ? null : row.getUpdatedBy();
        LocalDateTime updatedAt = row == null ? null : row.getUpdatedAt();

        return new AiLlmConfigVO(
                cfg.enabled(),
                cfg.baseUrl(),
                maskApiKey(cfg.apiKey()),
                cfg.model(),
                cfg.normalizedTimeoutMs(),
                cfg.normalizedMaxRetries(),
                cfg.source(),
                updatedBy,
                updatedAt);
    }

    @Transactional
    public AiLlmConfigVO updateConfig(AiLlmConfigDTO dto) {
        AiLlmConfig existing = configMapper.findActive();
        validate(dto, existing);

        Long adminId = resolveAdminId();
        String apiKeyCipher = resolveCipher(dto, existing);

        AiLlmConfig config = new AiLlmConfig();
        config.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        config.setBaseUrl(safe(dto.getBaseUrl()));
        config.setApiKeyCipher(apiKeyCipher);
        config.setModel(safe(dto.getModel()));
        config.setTimeoutMs(dto.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : dto.getTimeoutMs());
        config.setMaxRetries(dto.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : dto.getMaxRetries());
        config.setUpdatedBy(adminId);
        configMapper.upsert(config);

        configProvider.invalidateCache();

        log.info("管理员 id={} 更新了 AI LLM 配置（enabled={}, model={}）",
                adminId, config.isEnabled(), config.getModel());

        return getConfig();
    }

    private void validate(AiLlmConfigDTO dto, AiLlmConfig existing) {
        // 加密密钥未配置时拒绝写入（放最前：后续 decrypt 旧 key 才安全）
        if (!crypto.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
        }
        Integer timeoutMs = dto.getTimeoutMs();
        if (timeoutMs != null && timeoutMs <= 0) {
            throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
        }
        Integer maxRetries = dto.getMaxRetries();
        if (maxRetries != null && maxRetries < 0) {
            throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
        }
        // apiKey 传了就必须非空白（null/省略 = 保留旧值，空白 = 非法）—— 与 DTO 契约一致，不限于 enabled
        if (dto.getApiKey() != null && dto.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
        }
        if (Boolean.TRUE.equals(dto.getEnabled())) {
            if (isBlank(dto.getBaseUrl()) || isBlank(dto.getModel())) {
                throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
            }
            // 启用时必须有可用 key：新 key 或已存在的非空旧 key。
            // 防止首次启用省略 apiKey → 写入空 key → DB 配置遮蔽 env fallback 却又不可用。
            boolean hasNewKey = dto.getApiKey() != null && !dto.getApiKey().isBlank();
            if (!hasNewKey && !hasNonBlankExistingKey(existing)) {
                throw new BusinessException(ErrorCode.AI_LLM_CONFIG_INVALID);
            }
        }
    }

    private String resolveCipher(AiLlmConfigDTO dto, AiLlmConfig existing) {
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            return crypto.encrypt(dto.getApiKey());
        }
        if (existing != null && existing.getApiKeyCipher() != null) {
            return existing.getApiKeyCipher();
        }
        // disabled 且首次创建未提供 apiKey：加密空串占位（保证 api_key_cipher NOT NULL；
        // enabled=true 时 validate 已保证有可用 key，不会走到这里）
        return crypto.encrypt("");
    }

    /** 旧 key 是否存在且解密后非空（密钥轮换等导致不可解密时视为无可用 key）。 */
    private boolean hasNonBlankExistingKey(AiLlmConfig existing) {
        if (existing == null || existing.getApiKeyCipher() == null) {
            return false;
        }
        try {
            return !crypto.decrypt(existing.getApiKeyCipher()).isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private Long resolveAdminId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            // 理论上不会到达：SecurityConfig 已保证 /api/admin/** 经 ADMIN portal 鉴权
            return null;
        }
        AdminUser adminUser = adminMapper.findByUserId(userId);
        return adminUser == null ? null : adminUser.getId();
    }

    static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() < 8) {
            return MASK;
        }
        return apiKey.substring(0, 2) + MASK + apiKey.substring(apiKey.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
