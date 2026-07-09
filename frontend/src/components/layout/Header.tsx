"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Plane, Menu, X } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { useAuth } from "@/contexts/AuthContext"
import { useState } from "react"

const NAV_ITEMS = [
  { href: "/", label: "首页" },
  { href: "/flights", label: "航班查询" },
  { href: "/ai-assistant", label: "AI 助手" },
  { href: "/orders", label: "我的订单" },
  { href: "/waitlist", label: "我的候补" },
]

export function Header() {
  const pathname = usePathname()
  const { user, isAuthenticated, logout, isLoading } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)
  const isActive = (href: string) =>
    href === "/" ? pathname === href : pathname === href || pathname.startsWith(`${href}/`)

  return (
    <header className="sticky top-0 z-50 w-full border-b border-slate-200 bg-white/95 backdrop-blur supports-[backdrop-filter]:bg-white/80">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 font-bold text-xl text-primary">
          <Plane className="h-6 w-6" />
          <span className="hidden sm:inline">SkyBooker 云航智订</span>
        </Link>

        {/* Desktop Nav */}
        <nav className="hidden md:flex items-center gap-1">
          {NAV_ITEMS.map((item) => (
            <Button
              key={item.href}
              variant={isActive(item.href) ? "secondary" : "ghost"}
              render={<Link href={item.href}>{item.label}</Link>}
              nativeButton={false}
            />
          ))}
        </nav>

        {/* Right */}
        <div className="flex items-center gap-2">
          {isLoading ? (
            <div className="h-8 w-8 rounded-full bg-muted animate-pulse" />
          ) : isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger
                render={
                  <Button variant="ghost" className="relative h-8 w-8 rounded-full">
                    <Avatar className="h-8 w-8">
                      <AvatarFallback className="bg-primary/10 text-primary text-sm">
                        {user.nickname?.charAt(0)?.toUpperCase() || "U"}
                      </AvatarFallback>
                    </Avatar>
                  </Button>
                }
              />
              <DropdownMenuContent className="w-48" align="end">
                <div className="px-2 py-1.5">
                  <p className="text-sm font-medium">{user.nickname}</p>
                  <p className="text-xs text-muted-foreground">{user.email}</p>
                </div>
                <DropdownMenuSeparator />
                <DropdownMenuItem render={<Link href="/orders">我的订单</Link>} />
                <DropdownMenuItem render={<Link href="/waitlist">我的候补</Link>} />
                <DropdownMenuItem render={<Link href="/settings">账号设置</Link>} />
                <DropdownMenuSeparator />
                <DropdownMenuItem onClick={() => logout()}>退出登录</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <Button render={<Link href="/login">登录</Link>} size="sm" nativeButton={false} />
          )}

          {/* Mobile menu toggle */}
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={() => setMobileOpen(!mobileOpen)}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        </div>
      </div>

      {/* Mobile Nav */}
      {mobileOpen && (
        <nav className="md:hidden border-t border-slate-200 bg-white px-4 py-3 space-y-1">
          {NAV_ITEMS.map((item) => (
            <Button
              key={item.href}
              variant={isActive(item.href) ? "secondary" : "ghost"}
              className="w-full justify-start"
              render={<Link href={item.href}>{item.label}</Link>}
              onClick={() => setMobileOpen(false)}
              nativeButton={false}
            />
          ))}
        </nav>
      )}
    </header>
  )
}
