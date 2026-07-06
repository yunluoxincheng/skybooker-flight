package com.skybooker.ai.tool;

import com.skybooker.ai.config.LlmEffectiveConfig;
import com.skybooker.ai.enums.DomainIntent;
import com.skybooker.ai.parser.IntentParserService;
import com.skybooker.ai.service.DomainReplyComposer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TravelPlanTool {

    private final DomainReplyComposer domainReplyComposer;
    private final IntentParserService ruleIntentParserService;

    public TravelPlanResult advise(String message, LlmEffectiveConfig cfg) {
        String destination = ruleIntentParserService.parseDestinationSwitchCity(message);
        if (destination == null) {
            destination = ruleIntentParserService.parseFirstKnownDestinationCity(message);
        }
        String reply = domainReplyComposer.compose(DomainIntent.TRAVEL_CHAT, message, cfg);
        if (destination != null && isGeneric(reply)) {
            reply = destination + "适合做轻量行程：第一天安排核心景点和城市漫步，第二天留给特色街区、美食和休息，第三天按返程时间选择近距离补充项目。需要具体航班时，请告诉我出发城市和出发日期。";
        }
        return new TravelPlanResult(reply, destination);
    }

    private boolean isGeneric(String reply) {
        return reply == null
                || reply.contains("我是 SkyBooker")
                || reply.contains("您是想查询机票");
    }
}
