package com.skybooker.admin.vo;

import java.util.List;

/**
 * 管理端用户删除预检查结果。
 *
 * <p>硬删除策略下,只有零业务数据的"干净账号"才能物理删除(避免破坏 ticket_order 等
 * 表的 FK RESTRICT);有任何引用记录时通过 {@link #blockReasons} 引导管理员改用禁用。
 */
public record UserDeleteCheckVO(
        boolean canDelete,
        int orderCount,
        int passengerCount,
        int waitlistCount,
        int refundOrChangeCount,
        boolean oauthBound,
        int aiSessionCount,
        int aiRecommendationCount,
        List<String> blockReasons
) {
}
