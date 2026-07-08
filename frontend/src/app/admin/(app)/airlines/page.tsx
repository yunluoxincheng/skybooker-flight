"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { useForm, type Resolver } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, Pencil, Plus } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
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
import type { AirlineVO } from "@/types/admin"

const airlineSchema = z.object({
  code: z.string().trim().min(1, "请输入航司代码").max(20, "航司代码长度不能超过 20"),
  name: z.string().trim().min(1, "请输入航司名称").max(100, "航司名称长度不能超过 100"),
  logoUrl: z.string().trim().max(255, "Logo URL 长度不能超过 255").optional().or(z.literal("")),
})

type AirlineFormValues = z.infer<typeof airlineSchema>

function formatDateTime(iso?: string | null) {
  return `${formatDateFull(iso)} ${formatTime(iso)}`
}

export default function AdminAirlinesPage() {
  const [airlines, setAirlines] = useState<AirlineVO[]>([])
  const [keyword, setKeyword] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionErr, setActionErr] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingAirline, setEditingAirline] = useState<AirlineVO | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AirlineFormValues>({
    resolver: zodResolver(airlineSchema) as Resolver<AirlineFormValues>,
    defaultValues: { code: "", name: "", logoUrl: "" },
  })

  const fetchAirlines = useCallback(async () => {
    setIsLoading(true)
    try {
      const data = await adminApi.getAirlines({ page: 1, size: 100 })
      setAirlines(data.records)
      setError(null)
    } catch (err) {
      setError((err as ApiError).message || "加载航司失败")
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchAirlines()
  }, [fetchAirlines])

  const filteredAirlines = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase()
    if (!normalizedKeyword) return airlines

    return airlines.filter((airline) =>
      [airline.code, airline.name].some((value) => value.toLowerCase().includes(normalizedKeyword))
    )
  }, [airlines, keyword])

  const openAdd = () => {
    setEditingAirline(null)
    setActionErr(null)
    reset({ code: "", name: "", logoUrl: "" })
    setDialogOpen(true)
  }

  const openEdit = (airline: AirlineVO) => {
    setEditingAirline(airline)
    setActionErr(null)
    reset({
      code: airline.code,
      name: airline.name,
      logoUrl: airline.logoUrl ?? "",
    })
    setDialogOpen(true)
  }

  const onSubmit = async (values: AirlineFormValues) => {
    setIsSubmitting(true)
    setActionErr(null)
    try {
      if (editingAirline) {
        await adminApi.updateAirline(editingAirline.id, {
          name: values.name,
          logoUrl: values.logoUrl || undefined,
        })
      } else {
        await adminApi.createAirline({
          code: values.code,
          name: values.name,
          logoUrl: values.logoUrl || undefined,
        })
      }

      setDialogOpen(false)
      await fetchAirlines()
    } catch (err) {
      setActionErr((err as ApiError).message || "保存航司失败")
    } finally {
      setIsSubmitting(false)
    }
  }

  const toggleStatus = async (airline: AirlineVO) => {
    setBusyId(airline.id)
    setActionErr(null)
    try {
      if (airline.status === "ENABLED") {
        await adminApi.disableAirline(airline.id)
      } else {
        await adminApi.enableAirline(airline.id)
      }
      await fetchAirlines()
    } catch (err) {
      setActionErr((err as ApiError).message || "更新航司状态失败")
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold">航司管理</h1>
          <p className="text-sm text-muted-foreground">维护航班表单使用的航空公司主数据</p>
        </div>
        <Button size="sm" onClick={openAdd} className="gap-1">
          <Plus className="h-4 w-4" />
          新增航司
        </Button>
      </div>

      {actionErr && (
        <div className="rounded-lg bg-destructive/10 px-4 py-2.5 text-sm text-destructive">{actionErr}</div>
      )}

      <div className="flex flex-wrap gap-3">
        <Input
          className="w-full md:w-72"
          placeholder="搜索代码或名称..."
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Button variant="outline" size="sm" onClick={() => setKeyword("")}>
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
        <div className="rounded-xl border bg-white overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>代码</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>Logo URL</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="w-[120px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredAirlines.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    {airlines.length === 0 ? "暂无航司数据" : "没有匹配的航司"}
                  </TableCell>
                </TableRow>
              ) : (
                filteredAirlines.map((airline) => (
                  <TableRow key={airline.id}>
                    <TableCell className="font-medium">{airline.code}</TableCell>
                    <TableCell>{airline.name}</TableCell>
                    <TableCell className="max-w-[280px] truncate text-sm text-muted-foreground">
                      {airline.logoUrl || "--"}
                    </TableCell>
                    <TableCell>
                      {airline.status === "ENABLED" ? (
                        <Badge className="text-xs">启用中</Badge>
                      ) : (
                        <Badge variant="secondary" className="text-xs">已禁用</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {formatDateTime(airline.updatedAt)}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger render={<Button variant="ghost" size="sm">操作</Button>} />
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => openEdit(airline)}>
                            <Pencil className="mr-2 h-3.5 w-3.5" />
                            编辑
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            disabled={busyId === airline.id}
                            onClick={() => toggleStatus(airline)}
                            variant={airline.status === "ENABLED" ? "destructive" : "default"}
                          >
                            {busyId === airline.id ? (
                              <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
                            ) : null}
                            {airline.status === "ENABLED" ? "禁用" : "启用"}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingAirline ? "编辑航司" : "新增航司"}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="mt-2 space-y-4">
            {actionErr && (
              <div className="rounded-lg bg-destructive/10 px-3 py-2 text-xs text-destructive">{actionErr}</div>
            )}
            <div className="grid gap-4">
              <div className="space-y-1.5">
                <Label>航司代码</Label>
                <Input
                  {...register("code")}
                  disabled={Boolean(editingAirline)}
                  placeholder="CA"
                />
                {editingAirline && (
                  <p className="text-xs text-muted-foreground">航司代码创建后不可修改</p>
                )}
                {errors.code && <p className="text-xs text-destructive">{errors.code.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>航司名称</Label>
                <Input {...register("name")} placeholder="中国国际航空" />
                {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
              </div>
              <div className="space-y-1.5">
                <Label>Logo URL</Label>
                <Input {...register("logoUrl")} placeholder="https://example.com/logo.png" />
                {errors.logoUrl && <p className="text-xs text-destructive">{errors.logoUrl.message}</p>}
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="ghost" onClick={() => setDialogOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
                {editingAirline ? "保存" : "创建"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
