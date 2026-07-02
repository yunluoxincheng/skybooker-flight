package com.skybooker.ai.enums;

/**
 * AI 助手回复类型契约。
 *
 * <p>非搜索回复：{@link #TRAVEL_CHAT}（旅游闲聊/建议/问候）、{@link #BOOKING_HELP}（平台功能说明）、
 * {@link #OUT_OF_SCOPE}（越界拒绝）。搜索回复：{@link #FOLLOW_UP}（缺槽追问）、
 * {@link #FLIGHT_RECOMMENDATION}（数据库-backed 推荐）、{@link #NO_RESULT}（无结果）。</p>
 */
public enum AiReplyType {
    TRAVEL_CHAT,
    BOOKING_HELP,
    OUT_OF_SCOPE,
    FLIGHT_RECOMMENDATION,
    FOLLOW_UP,
    NO_RESULT
}
