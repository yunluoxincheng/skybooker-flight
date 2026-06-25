package com.skybooker.ai.config;

import com.skybooker.ai.entity.AiLlmConfig;
import com.skybooker.ai.mapper.AiLlmConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DynamicLlmConfigProviderTest {

    /** 32 字节 ASCII，base64 编码后作为 AES-256 密钥。 */
    private static final String ENCODED_KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private AiLlmConfigMapper mapper;
    private LlmConfigCrypto crypto;
    private AiLlmProperties properties;

    @BeforeEach
    void setUp() {
        mapper = mock(AiLlmConfigMapper.class);
        crypto = new LlmConfigCrypto(ENCODED_KEY);
        properties = new AiLlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://env.example/v1");
        properties.setApiKey("env-key");
        properties.setModel("env-model");
    }

    @Test
    void getConfig_dbHasRow_returnsDbValuesWithDecryptedApiKey() {
        AiLlmConfig row = new AiLlmConfig();
        row.setEnabled(true);
        row.setBaseUrl("https://db.example/v1");
        row.setApiKeyCipher(crypto.encrypt("secret-db-key"));
        row.setModel("db-model");
        row.setTimeoutMs(5000);
        row.setMaxRetries(2);
        when(mapper.findActive()).thenReturn(row);

        DynamicLlmConfigProvider provider = new DynamicLlmConfigProvider(mapper, properties, crypto);
        LlmEffectiveConfig cfg = provider.getConfig();

        assertThat(cfg.source()).isEqualTo(DynamicLlmConfigProvider.SOURCE_DB);
        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.baseUrl()).isEqualTo("https://db.example/v1");
        assertThat(cfg.apiKey()).isEqualTo("secret-db-key");
        assertThat(cfg.model()).isEqualTo("db-model");
        assertThat(cfg.timeoutMs()).isEqualTo(5000);
        assertThat(cfg.maxRetries()).isEqualTo(2);
        assertThat(cfg.isConfigured()).isTrue();
    }

    @Test
    void getConfig_dbEmpty_fallsBackToEnvDefaults() {
        when(mapper.findActive()).thenReturn(null);

        DynamicLlmConfigProvider provider = new DynamicLlmConfigProvider(mapper, properties, crypto);
        LlmEffectiveConfig cfg = provider.getConfig();

        assertThat(cfg.source()).isEqualTo(DynamicLlmConfigProvider.SOURCE_ENV);
        assertThat(cfg.baseUrl()).isEqualTo("https://env.example/v1");
        assertThat(cfg.apiKey()).isEqualTo("env-key");
        assertThat(cfg.model()).isEqualTo("env-model");
    }

    @Test
    void getConfig_cachesWithinTtl_avoidsRepeatedDbRead() {
        when(mapper.findActive()).thenReturn(null);

        DynamicLlmConfigProvider provider = new DynamicLlmConfigProvider(mapper, properties, crypto);
        provider.getConfig();
        provider.getConfig();

        verify(mapper, times(1)).findActive();
    }

    @Test
    void invalidateCache_forcesNextReadToHitDbAgain() {
        when(mapper.findActive()).thenReturn(null);

        DynamicLlmConfigProvider provider = new DynamicLlmConfigProvider(mapper, properties, crypto);
        provider.getConfig();
        provider.invalidateCache();
        provider.getConfig();

        verify(mapper, times(2)).findActive();
    }

    @Test
    void getConfig_decryptFailure_fallsBackToEnvWithoutThrowing() {
        AiLlmConfig row = new AiLlmConfig();
        row.setEnabled(true);
        row.setBaseUrl("https://db.example/v1");
        row.setApiKeyCipher("not-a-valid-cipher");
        row.setModel("db-model");
        when(mapper.findActive()).thenReturn(row);

        DynamicLlmConfigProvider provider = new DynamicLlmConfigProvider(mapper, properties, crypto);
        LlmEffectiveConfig cfg = provider.getConfig();

        // 密钥无法解密 → fallback env，不抛异常、不阻断
        assertThat(cfg.source()).isEqualTo(DynamicLlmConfigProvider.SOURCE_ENV);
        assertThat(cfg.apiKey()).isEqualTo("env-key");
    }

    @Test
    void getConfig_cryptoUnavailable_fallsBackToEnvWithoutThrowing() {
        AiLlmConfig row = new AiLlmConfig();
        row.setEnabled(true);
        row.setBaseUrl("https://db.example/v1");
        row.setApiKeyCipher("any");
        row.setModel("db-model");
        when(mapper.findActive()).thenReturn(row);

        LlmConfigCrypto unavailableCrypto = new LlmConfigCrypto("");  // 无密钥
        DynamicLlmConfigProvider provider =
                new DynamicLlmConfigProvider(mapper, properties, unavailableCrypto);
        LlmEffectiveConfig cfg = provider.getConfig();

        assertThat(cfg.source()).isEqualTo(DynamicLlmConfigProvider.SOURCE_ENV);
    }
}
