/**
 * 获取用户展示名称：优先 nickname，其次 realName，最后用邮箱兜底。
 * 适用于 UserAdminVO 及其他包含 nickname / realName / email 的用户对象。
 */
export function getDisplayName(user: {
  nickname?: string | null
  realName?: string | null
  email: string
}): string {
  return user.nickname || user.realName || user.email
}
