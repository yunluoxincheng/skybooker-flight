package com.skybooker.ai.parser;

public class LlmIntentParseException extends RuntimeException {

    public LlmIntentParseException(String message) {
        super(message);
    }

    public LlmIntentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
