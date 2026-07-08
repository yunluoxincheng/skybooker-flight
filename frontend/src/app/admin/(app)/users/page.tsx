"use client"

import { useCallback, useEffect, useState } from "react"
import { ChevronLeft, ChevronRight, Loader2, Plus, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatDateFull, formatTime } from "@/lib/date-utils"
import type { ApiError } from "@/lib/request"
import * as adminApi from "@/services/adminApi"
import type { UserAdminVO } from "@/types/admin"
import { CreateUserDialog } from "./_components/CreateUserDialog"
import { DeleteUserDialog } from "./_components/DeleteUserDialog"

function formatDateTime(iso: string) {
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserAdminVO[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [emailFilter, setEmailFilter] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)

  const [confirmOpen, setConfirmOpen] = useState(false)
  const [confirmAction, setConfirmAction] = useState<{
    user: UserAdminVO
    action: "disable" | "enable"
  } | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteUser, setDeleteUser] = useState<UserAdminVO | null>(null)

  const fetchUsers = useCallback(async () => {
    try {
      const params: Record<string, string | number | boolean | undefined> = {
        page,
        size: 10,
        role: "USER",
      }
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
  }, [emailFilter, page])

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  const refreshUsers = useCallback(async () => {
    setIsLoading(true)
    await fetchUsers()
  }, [fetchUsers])

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
      await refreshUsers()
    } catch (err) {
      setActionErr((err as ApiError).message || "操作失败")
    } finally {
      setActionLoading(false)
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 10))

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">用户管理</h1>
          <p className="text-sm text-muted-foreground">
            支持创建普通用户、启用/禁用和删除前阻断检查。
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="h-4 w-4" />
          新增用户
        </Button>
      </div>

      {actionErr && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">
          {actionErr}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <Input
          placeholder="搜索邮箱..."
          className="w-60"
          value={emailFilter}
          onChange={(event) => {
            setIsLoading(true)
            setEmailFilter(event.target.value)
            setPage(1)
          }}
        />
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            setIsLoading(true)
            setEmailFilter("")
            setPage(1)
          }}
        >
          清除
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, index) => (
            <Skeleton key={index} className="h-12 w-full rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center text-destructive">
          {error}
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-white">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID</TableHead>
                <TableHead>姓名</TableHead>
                <TableHead>邮箱</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>注册时间</TableHead>
                <TableHead className="w-[180px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    暂无用户数据
                  </TableCell>
                </TableRow>
              ) : (
                users.map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>{user.id}</TableCell>
                    <TableCell className="font-medium">{user.realName}</TableCell>
                    <TableCell>{user.email}</TableCell>
                    <TableCell>
                      {user.status === "NORMAL" ? (
                        <Badge variant="default" className="text-xs">正常</Badge>
                      ) : (
                        <Badge variant="destructive" className="text-xs">已禁用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{formatDateTime(user.createdAt)}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {user.status === "NORMAL" ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setConfirmAction({ user, action: "disable" })
                              setConfirmOpen(true)
                            }}
                          >
                            禁用
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                              setConfirmAction({ user, action: "enable" })
                              setConfirmOpen(true)
                            }}
                          >
                            启用
                          </Button>
                        )}
                        <Button variant="destructive" size="sm" onClick={() => setDeleteUser(user)}>
                          <Trash2 className="h-3.5 w-3.5" />
                          删除
                        </Button>
                      </div>
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
          <Button
            variant="outline"
            size="sm"
            disabled={page <= 1}
            onClick={() => {
              setIsLoading(true)
              setPage(page - 1)
            }}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">
            {page} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages}
            onClick={() => {
              setIsLoading(true)
              setPage(page + 1)
            }}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认操作</DialogTitle>
            <DialogDescription>
              {confirmAction?.action === "disable"
                ? `确定要禁用用户「${confirmAction?.user.realName}」吗？`
                : `确定要启用用户「${confirmAction?.user.realName}」吗？`}
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setConfirmOpen(false)}>
              取消
            </Button>
            <Button
              variant={confirmAction?.action === "disable" ? "destructive" : "default"}
              onClick={doAction}
              disabled={actionLoading}
            >
              {actionLoading && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
              确认
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <CreateUserDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSuccess={refreshUsers}
      />

      <DeleteUserDialog
        open={Boolean(deleteUser)}
        onOpenChange={(open) => {
          if (!open) setDeleteUser(null)
        }}
        user={deleteUser}
        onSuccess={refreshUsers}
      />
    </div>
  )
}
