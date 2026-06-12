package com.skybooker.ai.parser;

public interface IntentParser {

    ParsedCondition parse(String message);
}
