package com.skybooker.ai.enums;

/**
 * 领域意图：决定一条消息走哪个执行器。
 *
 * <ul>
 *   <li>{@link #TRAVEL_CHAT} — 旅游/出行领域的闲聊与建议（含问候），走 LLM 限话题回复。</li>
 *   <li>{@link #FLIGHT_QUERY} / {@link #FLIGHT_QUERY_CONTINUATION} — 涉及航班、机票、票价、
 *       余票、舱位、时刻等系统事实，必须经 parser + 内部接口/数据库回复。</li>
 *   <li>{@link #BOOKING_HELP} — 订票、退票、改签、选座、订单等平台功能说明，走模板/受控知识库。</li>
 *   <li>{@link #OUT_OF_SCOPE} — 非旅游出行问题，拒绝并引导回出行话题。</li>
 * </ul>
 */
public enum DomainIntent {
    TRAVEL_CHAT,
    FLIGHT_QUERY,
    FLIGHT_QUERY_CONTINUATION,
    BOOKING_HELP,
    OUT_OF_SCOPE
}
