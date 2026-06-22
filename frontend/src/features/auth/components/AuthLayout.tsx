import type { ReactNode } from "react"
import { Plane, Cloud, Sparkles } from "lucide-react"

interface AuthLayoutProps {
  title: string
  subtitle?: string
  children: ReactNode
}

export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <div className="flex min-h-screen">
      {/* 左侧品牌展示区 */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-primary via-blue-600 to-indigo-700 relative overflow-hidden">
        <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PHBhdGggZD0iTTM2IDM0djJhMiAyIDAgMDEtMiAyaC0ydjJhMiAyIDAgMDEtMiAySDI2di0yaC0yYTIgMiAwIDAxLTItMnYtMmgtMnYtMmgydi0yaC0ydi0yaDJ2LTJoLTJ2LTJoMnYtMmgyVjE0SDIydi0yaDJ2LTJoMnYySTR2MmgyVjE0aDJ2MmgydjJoMnYyaC0ydjJoMnYyaC0ydjJoLTJ6IiBvcGFjaXR5PSIuMyIvPjwvZz48L2c+PC9zdmc+')] opacity-50" />
        <div className="relative flex flex-col justify-center px-12 text-white">
          <div className="mb-8 flex items-center gap-3">
            <Plane className="h-12 w-12" />
            <h1 className="text-3xl font-bold tracking-tight">SkyBooker</h1>
          </div>
          <p className="text-2xl font-semibold mb-3">云航智订</p>
          <p className="text-lg text-white/80 leading-relaxed">
            AI 驱动的智能航班预订系统
          </p>
          <div className="mt-12 grid grid-cols-2 gap-6">
            <div className="flex items-start gap-3">
              <Sparkles className="h-5 w-5 mt-0.5 text-white/70" />
              <div>
                <p className="font-medium">AI 智能推荐</p>
                <p className="text-sm text-white/70">自然语言搜索航班</p>
              </div>
            </div>
            <div className="flex items-start gap-3">
              <Cloud className="h-5 w-5 mt-0.5 text-white/70" />
              <div>
                <p className="font-medium">可视化选座</p>
                <p className="text-sm text-white/70">实时座位图选择</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 右侧表单卡片 */}
      <div className="flex-1 flex items-center justify-center px-4 sm:px-8 bg-slate-50">
        <div className="w-full max-w-md">
          {/* 移动端 Logo */}
          <div className="lg:hidden mb-8 text-center">
            <Plane className="h-10 w-10 text-primary mx-auto" />
            <h1 className="mt-2 text-2xl font-bold text-primary">SkyBooker 云航智订</h1>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
            <div className="mb-6">
              <h2 className="text-xl font-bold">{title}</h2>
              {subtitle && (
                <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>
              )}
            </div>
            {children}
          </div>
        </div>
      </div>
    </div>
  )
}
