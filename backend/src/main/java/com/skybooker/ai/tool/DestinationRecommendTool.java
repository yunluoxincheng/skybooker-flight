package com.skybooker.ai.tool;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DestinationRecommendTool {

    public DestinationRecommendation recommend(String message) {
        String text = message == null ? "" : message;
        List<String> candidates = candidatesFor(text);
        String primary = candidates.getFirst();
        String replyText = "可以优先考虑" + primary + "。如果您想看海，" + primary
                + "的海岸线、度假配套和短途行程都比较成熟；备选还可以看"
                + String.join("、", candidates.subList(1, candidates.size()))
                + "。如果要继续查机票，请告诉我出发城市和出发日期。";

        List<Map<String, String>> quickActions = List.of(
                Map.of("label", "查去" + primary + "的机票", "value", "帮我查去" + primary + "的机票"),
                Map.of("label", "换个目的地", "value", "再推荐一个目的地")
        );
        return new DestinationRecommendation(replyText, primary, candidates, quickActions);
    }

    private List<String> candidatesFor(String text) {
        if (containsAny(text, List.of("海", "海边", "看海", "沙滩", "潜水"))) {
            return List.of("三亚", "厦门", "青岛");
        }
        if (containsAny(text, List.of("历史", "博物馆", "古城", "人文"))) {
            return List.of("西安", "南京", "北京");
        }
        if (containsAny(text, List.of("美食", "吃", "火锅"))) {
            return List.of("成都", "重庆", "长沙");
        }
        if (containsAny(text, List.of("周末", "三天", "3天", "短途"))) {
            return List.of("厦门", "成都", "杭州");
        }
        return List.of("三亚", "成都", "西安");
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
