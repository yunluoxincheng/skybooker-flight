package com.skybooker.ai.parser;

public interface LlmChatClient {

    String complete(String systemPrompt, String userPrompt);
}
