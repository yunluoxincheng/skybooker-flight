"use client"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import { useAdminAuth } from "@/contexts/AdminAuthContext"
import { LogOut } from "lucide-react"

export function AdminHeader() {
  const { admin, logout } = useAdminAuth()

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center justify-end border-b border-slate-200 bg-white px-6 ml-60">
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <Button variant="ghost" className="flex items-center gap-2">
              <Avatar className="h-8 w-8">
                <AvatarFallback className="bg-primary/10 text-primary text-sm">
                  {admin?.realName?.charAt(0)?.toUpperCase() || "A"}
                </AvatarFallback>
              </Avatar>
              <span className="hidden sm:inline text-sm font-medium">
                {admin?.realName || "管理员"}
              </span>
            </Button>
          }
        />
        <DropdownMenuContent className="w-48" align="end">
          <div className="px-2 py-1.5">
            <p className="text-sm font-medium">{admin?.realName}</p>
            <p className="text-xs text-muted-foreground">
              {admin?.username} · 管理员
            </p>
          </div>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => logout()}>
            <LogOut className="mr-2 h-4 w-4" />
            退出登录
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  )
}
