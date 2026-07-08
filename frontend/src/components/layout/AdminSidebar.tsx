"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Plane, LayoutDashboard, PlaneTakeoff, Building2, MapPin, Package, Users, Cpu } from "lucide-react"
import { cn } from "@/lib/utils"

const NAV_ITEMS = [
  { href: "/admin/dashboard", label: "数据看板", icon: LayoutDashboard },
  { href: "/admin/flights", label: "航班管理", icon: PlaneTakeoff },
  { href: "/admin/airlines", label: "航司管理", icon: Building2 },
  { href: "/admin/airports", label: "机场管理", icon: MapPin },
  { href: "/admin/orders", label: "订单管理", icon: Package },
  { href: "/admin/users", label: "用户管理", icon: Users },
  { href: "/admin/ai-config", label: "AI 配置", icon: Cpu },
]

export function AdminSidebar() {
  const pathname = usePathname()

  return (
    <aside className="fixed left-0 top-0 z-40 h-screen w-60 border-r border-slate-200 bg-white flex flex-col">
      {/* Logo */}
      <Link
        href="/admin/dashboard"
        className="flex items-center gap-2 h-16 px-6 border-b border-slate-200 font-bold text-lg text-primary"
      >
        <Plane className="h-5 w-5" />
        <span>管理后台</span>
      </Link>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
        {NAV_ITEMS.map((item) => {
          const isActive =
            pathname === item.href ||
            (item.href !== "/admin/dashboard" && pathname.startsWith(item.href))
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-slate-100 hover:text-foreground"
              )}
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
