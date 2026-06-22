"use client"

import { Suspense, useState } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import { SlidersHorizontal, ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle, DrawerTrigger } from "@/components/ui/drawer"
import { Skeleton } from "@/components/ui/skeleton"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightSearchCard } from "@/components/common/FlightSearchCard"
import { FlightCard } from "@/components/common/FlightCard"
import { useFlightSearch } from "@/features/flights/hooks/useFlightSearch"

function FlightsContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { flights, total, page, size, isLoading, error } = useFlightSearch()
  const [filterOpen, setFilterOpen] = useState(false)

  const updateParam = (key: string, value: string) => {
    const params = new URLSearchParams(searchParams.toString())
    if (value) {
      params.set(key, value)
    } else {
      params.delete(key)
    }
    params.delete("page") // 重置页码
    router.push(`/flights?${params.toString()}`)
  }

  const goToPage = (newPage: number) => {
    const params = new URLSearchParams(searchParams.toString())
    params.set("page", String(newPage))
    router.push(`/flights?${params.toString()}`)
  }

  const totalPages = Math.max(1, Math.ceil(total / size))

  const currentSortBy = searchParams.get("sortBy") || ""
  const currentDirectOnly = searchParams.get("directOnly") === "true"
  const currentTimeRange = searchParams.get("departureTimeRange") || ""

  const TIME_RANGES = [
    { value: "00:00-06:00", label: "凌晨 00:00-06:00" },
    { value: "06:00-12:00", label: "上午 06:00-12:00" },
    { value: "12:00-18:00", label: "下午 12:00-18:00" },
    { value: "18:00-24:00", label: "晚间 18:00-24:00" },
  ]

  const SORT_OPTIONS = [
    { value: "", label: "综合推荐" },
    { value: "price", label: "价格最低" },
    { value: "duration", label: "飞行时间短" },
    { value: "departure", label: "起飞最早" },
    { value: "seats", label: "余票最多" },
  ]

  const renderFilters = () => (
    <div className="space-y-5">
      {/* 排序 */}
      <div>
        <h4 className="text-sm font-medium mb-2">排序方式</h4>
        <div className="flex flex-wrap gap-2">
          {SORT_OPTIONS.map((opt) => (
            <Button
              key={opt.value}
              variant={currentSortBy === opt.value ? "default" : "outline"}
              size="sm"
              onClick={() => updateParam("sortBy", opt.value)}
            >
              {opt.label}
            </Button>
          ))}
        </div>
      </div>

      <Separator />

      {/* 直飞筛选 */}
      <div className="flex items-center gap-2">
        <Checkbox
          id="directOnly"
          checked={currentDirectOnly}
          onCheckedChange={(checked) => updateParam("directOnly", checked ? "true" : "")}
        />
        <Label htmlFor="directOnly" className="text-sm cursor-pointer">
          仅显示直飞航班
        </Label>
      </div>

      <Separator />

      {/* 起飞时段 */}
      <div>
        <h4 className="text-sm font-medium mb-2">起飞时段</h4>
        <div className="space-y-2">
          {TIME_RANGES.map((t) => (
            <div key={t.value} className="flex items-center gap-2">
              <Checkbox
                id={`time-${t.value}`}
                checked={currentTimeRange === t.value}
                onCheckedChange={(checked) =>
                  updateParam("departureTimeRange", checked ? t.value : "")
                }
              />
              <Label htmlFor={`time-${t.value}`} className="text-sm cursor-pointer">
                {t.label}
              </Label>
            </div>
          ))}
        </div>
      </div>

      <Separator />

      {/* 价格区间 */}
      <div>
        <h4 className="text-sm font-medium mb-2">价格区间</h4>
        <div className="flex items-center gap-2">
          <Input
            type="number"
            placeholder="最低"
            className="w-24"
            defaultValue={searchParams.get("minPrice") || ""}
            onBlur={(e) => updateParam("minPrice", e.target.value)}
          />
          <span className="text-muted-foreground">—</span>
          <Input
            type="number"
            placeholder="最高"
            className="w-24"
            defaultValue={searchParams.get("maxPrice") || ""}
            onBlur={(e) => updateParam("maxPrice", e.target.value)}
          />
        </div>
      </div>
    </div>
  )

  return (
    <UserLayout>
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8">
        {/* 搜索栏 */}
        <FlightSearchCard
          compact
          defaultDepartureCity={searchParams.get("departureCity") || ""}
          defaultArrivalCity={searchParams.get("arrivalCity") || ""}
          defaultDepartureDate={searchParams.get("departureDate") || ""}
        />

        {/* 结果区域 */}
        <div className="mt-6 flex gap-6">
          {/* 桌面端筛选侧边栏 */}
          <aside className="hidden lg:block w-56 shrink-0">
            <div className="sticky top-24 rounded-xl border border-slate-200 bg-white p-4">
              <h3 className="font-semibold mb-4 flex items-center gap-2">
                <SlidersHorizontal className="h-4 w-4" /> 筛选
              </h3>
              {renderFilters()}
            </div>
          </aside>

          {/* 移动端筛选 Drawer */}
          <div className="lg:hidden">
            <Drawer open={filterOpen} onOpenChange={setFilterOpen}>
              <DrawerTrigger asChild>
                <Button variant="outline" size="sm" className="gap-2">
                  <SlidersHorizontal className="h-4 w-4" /> 筛选
                </Button>
              </DrawerTrigger>
              <DrawerContent>
                <DrawerHeader>
                  <DrawerTitle>筛选条件</DrawerTitle>
                </DrawerHeader>
                <div className="px-4 pb-6">{renderFilters()}</div>
              </DrawerContent>
            </Drawer>
          </div>

          {/* 航班列表 */}
          <div className="flex-1 min-w-0">
            {/* 结果统计 */}
            {!isLoading && (
              <p className="text-sm text-muted-foreground mb-4">
                {total > 0
                  ? `找到 ${total} 个航班，第 ${page}/${totalPages} 页`
                  : "未找到符合条件的航班"}
              </p>
            )}

            {/* Loading */}
            {isLoading && (
              <div className="space-y-4">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-40 w-full rounded-xl" />
                ))}
              </div>
            )}

            {/* Error */}
            {error && (
              <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-8 text-center">
                <p className="text-destructive mb-3">{error}</p>
                <Button variant="outline" onClick={() => window.location.reload()}>
                  重试
                </Button>
              </div>
            )}

            {/* 航班卡片列表 */}
            {!isLoading && !error && (
              <div className="space-y-4">
                {flights.map((flight) => (
                  <FlightCard key={flight.id} flight={flight} />
                ))}
              </div>
            )}

            {/* 空状态 */}
            {!isLoading && !error && flights.length === 0 && (
              <div className="rounded-xl border border-slate-200 bg-white p-12 text-center">
                <p className="text-muted-foreground mb-4">未找到符合条件的航班</p>
                <Button
                  variant="outline"
                  onClick={() => router.push("/flights")}
                >
                  清除筛选
                </Button>
              </div>
            )}

            {/* 分页 */}
            {totalPages > 1 && (
              <div className="flex items-center justify-center gap-2 mt-8">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page <= 1}
                  onClick={() => goToPage(page - 1)}
                >
                  <ChevronLeft className="h-4 w-4" /> 上一页
                </Button>
                {Array.from({ length: Math.min(totalPages, 5) }).map((_, i) => {
                  const startPage = Math.max(1, Math.min(page - 2, totalPages - 4))
                  const pageNum = startPage + i
                  if (pageNum > totalPages) return null
                  return (
                    <Button
                      key={pageNum}
                      variant={pageNum === page ? "default" : "outline"}
                      size="sm"
                      className="w-9"
                      onClick={() => goToPage(pageNum)}
                    >
                      {pageNum}
                    </Button>
                  )
                })}
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages}
                  onClick={() => goToPage(page + 1)}
                >
                  下一页 <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </UserLayout>
  )
}

export default function FlightsPage() {
  return (
    <Suspense
      fallback={
        <UserLayout>
          <div className="mx-auto max-w-7xl px-4 py-8">
            <Skeleton className="h-20 w-full rounded-xl mb-6" />
            <Skeleton className="h-96 w-full rounded-xl" />
          </div>
        </UserLayout>
      }
    >
      <FlightsContent />
    </Suspense>
  )
}
