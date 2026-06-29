"use client"

import { useEffect, useState, useCallback } from "react"
import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import type { ApiError } from "@/lib/request"

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserAdminVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [emailFilter, setEmailFilter] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)

  // 确认弹窗
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmAction, setConfirmAction] = useState<{
    user: UserAdminVO
    action: "disable" | "enable"
  } | null>(null)
  const [actionLoading, setActionLoading] = useState(false)

  const fetchUsers = useCallback(async () => {
    try {
      const params: Record<string, string | number | boolean | undefined> = { page, size: 10, role: "USER" }
      if (emailFilter) params.email = emailFilter
      const data = await adminApi.getUsers(params)
      setUsers(data.records)
      setTotal(data.total)
      setError(null)
    } catch (err) {
      setError((err as ApiError).message || "加载用户失败")
    } finally {
      setIsLoading(false)
    }
  }, [page, emailFilter])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  const doAction = async () => {
    if (!confirmAction) return
    setActionLoading(true)
    setActionErr(null)
    try {
      if (confirmAction.action === "disable") {
        await adminApi.disableUser(confirmAction.user.id)
      } else {
        await adminApi.enableUser(confirmAction.user.id)
      }
      setConfirmOpen(false)
      setIsLoading(true)
      fetchUsers()
    } catch (err) {
      setActionErr((err as ApiError).message || "操作失败")
    } finally {
      setActionLoading(false)
    }
  }

  const formatTime = (iso: string) => {
    return new Date(iso).toLocaleString("zh-CN")
  }

  const totalPages = Math.max(1, Math.ceil(total / 10))

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">用户管理</h1>

      {actionErr && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{actionErr}</div>
      )}

      {/* 筛选 */}
      <div className="flex flex-wrap gap-3">
        <Input
          placeholder="搜索邮箱..."
          className="w-60"
          value={emailFilter}
          onChange={(e) => { setIsLoading(true); setEmailFilter(e.target.value); setPage(1) }}
        />
        <Button variant="outline" size="sm" onClick={() => { setIsLoading(true); setEmailFilter(""); setPage(1) }}>清除</Button>
      </div>

      {/* Table */}
      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-12 w-full rounded-lg" />)}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">{error}</div>
      ) : (
        <div className="rounded-xl border bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID</TableHead>
                <TableHead>昵称</TableHead>
                <TableHead>邮箱</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>注册时间</TableHead>
                <TableHead className="w-[120px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-muted-foreground py-8">暂无用户数据</TableCell>
                </TableRow>
              ) : (
                users.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell>{u.id}</TableCell>
                    <TableCell className="font-medium">{u.nickname}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>
                      {u.status === "ENABLED" ? (
                        <Badge variant="default" className="text-xs">正常</Badge>
                      ) : (
                        <Badge variant="destructive" className="text-xs">已禁用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{formatTime(u.createdAt)}</TableCell>
                    <TableCell>
                      {u.status === "ENABLED" ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => { setConfirmAction({ user: u, action: "disable" }); setConfirmOpen(true) }}
                        >
                          禁用
                        </Button>
                      ) : (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => { setConfirmAction({ user: u, action: "enable" }); setConfirmOpen(true) }}
                        >
                          启用
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => { setIsLoading(true); setPage(page - 1) }}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">{page} / {totalPages}</span>
          <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => { setIsLoading(true); setPage(page + 1) }}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      {/* 确认弹窗 */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认操作</DialogTitle>
            <DialogDescription>
              {confirmAction?.action === "disable"
                ? `确定要禁用用户「${confirmAction?.user.nickname}」吗？`
                : `确定要启用用户「${confirmAction?.user.nickname}」吗？`}
            </DialogDescription>
          </DialogHeader>
          <div className="flex justify-end gap-2 mt-4">
            <Button variant="ghost" onClick={() => setConfirmOpen(false)}>取消</Button>
            <Button
              variant={confirmAction?.action === "disable" ? "destructive" : "default"}
              onClick={doAction}
              disabled={actionLoading}
            >
              {actionLoading && <Loader2 className="h-4 w-4 animate-spin mr-1" />}
              确认
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
