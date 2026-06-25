package com.skybooker.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skybooker.ai.config.LlmEffectiveConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleLlmChatClient implements LlmChatClient {

    private final ObjectMapper objectMapper;

    @Override
    public String complete(String systemPrompt, String userPrompt, LlmEffectiveConfig cfg) {
        int attempts = cfg.normalizedMaxRetries() + 1;
        RuntimeException lastFailure = null;

        for (int i = 0; i < attempts; i++) {
            try {
                return doComplete(systemPrompt, userPrompt, cfg);
            } catch (RuntimeException e) {
                lastFailure = e;
            }
        }

        throw new LlmIntentParseException("Provider request failed", lastFailure);
    }

    private String doComplete(String systemPrompt, String userPrompt, LlmEffectiveConfig cfg) {
        RestClient restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(cfg.baseUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.apiKey())
                .requestFactory(requestFactory(cfg))
                .build();

        Map<String, Object> body = Map.of(
                "model", cfg.model(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new LlmIntentParseException("Provider response missing message content");
            }
            return content.asText();
        } catch (LlmIntentParseException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmIntentParseException("Provider response is not valid chat-completions JSON", e);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private ClientHttpRequestFactory requestFactory(LlmEffectiveConfig cfg) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(cfg.normalizedTimeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
