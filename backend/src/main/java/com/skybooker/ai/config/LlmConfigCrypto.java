package com.skybooker.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * ai_llm_config.api_key_cipher 的对称加解密（AES-256-GCM）。
 *
 * <p>密钥来自环境变量 {@code AI_CONFIG_ENC_KEY}（base64 编码的 32 字节），与 {@code JWT_SECRET} 职责隔离。
 *
 * <p>降级语义（关键：不 crash 应用）：
 * <ul>
 *   <li>密钥缺失或格式非法 → 标记自身不可用，记 ERROR 日志，但不抛异常。</li>
 *   <li>解密侧（provider 读 DB）：不可用时由 provider fallback 环境变量默认值。</li>
 *   <li>加密侧（管理员 PUT）：不可用时由 service 直接返回 {@code AI_LLM_CONFIG_INVALID}，不落库。</li>
 * </ul>
 *
 * <p>密文编码：{@code base64(iv) + ":" + base64(ciphertext+tag)}，IV 随密文存储。
 */
@Slf4j
@Component
public class LlmConfigCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int AES_KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();
    private final boolean available;

    public LlmConfigCrypto(@Value("${ai.config-enc-key:}") String encodedKey) {
        this.secretKey = parseKey(encodedKey);
        this.available = this.secretKey != null;
        if (!this.available) {
            log.error("AI_CONFIG_ENC_KEY 未配置或格式非法，LLM 配置加密不可用；"
                    + "管理员将无法写入后台 LLM 配置，AI 对话仍可走环境变量 fallback。");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String encrypt(String plain) {
        if (!available) {
            throw new IllegalStateException("LLM 配置加密密钥不可用");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 配置加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (!available) {
            throw new IllegalStateException("LLM 配置加密密钥不可用");
        }
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        int sep = stored.indexOf(':');
        if (sep <= 0 || sep == stored.length() - 1) {
            throw new IllegalStateException("LLM 配置密文格式非法");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(stored.substring(0, sep));
            byte[] cipherText = Base64.getDecoder().decode(stored.substring(sep + 1));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("LLM 配置解密失败", e);
        }
    }

    private SecretKey parseKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey.trim());
            if (keyBytes.length != AES_KEY_BYTES) {
                log.error("AI_CONFIG_ENC_KEY 解码后为 {} 字节，AES-256 需要 32 字节", keyBytes.length);
                return null;
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("AI_CONFIG_ENC_KEY base64 解析失败：{}", e.getMessage());
            return null;
        }
    }
}
