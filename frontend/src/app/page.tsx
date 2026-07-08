import Link from "next/link"
import { Plane, Sparkles, ArmchairIcon, ListPlus, RefreshCw } from "lucide-react"
import { UserLayout } from "@/components/layout/UserLayout"
import { FlightSearchCard } from "@/components/common/FlightSearchCard"
import { HomeCTA } from "@/components/common/HomeCTA"

const HOT_ROUTES = [
  { from: "上海", to: "北京", price: 890 },
  { from: "广州", to: "成都", price: 680 },
  { from: "深圳", to: "杭州", price: 520 },
  { from: "北京", to: "三亚", price: 1250 },
]

const FEATURES = [
  {
    icon: Sparkles,
    title: "AI 智能推荐",
    desc: "自然语言描述需求，AI 精准匹配最优航班方案",
  },
  {
    icon: ArmchairIcon,
    title: "可视化选座",
    desc: "交互式座位图，靠窗/过道一目了然，实时锁定心仪座位",
  },
  {
    icon: ListPlus,
    title: "候补购票",
    desc: "心仪航班售罄？加入候补队列，有余票自动通知并支付",
  },
  {
    icon: RefreshCw,
    title: "在线退改签",
    desc: "全流程在线操作，无需电话客服，退票改签即时完成",
  },
]

export default function HomePage() {
  return (
    <UserLayout>
      {/* Hero Section */}
      <section className="bg-gradient-to-b from-primary/5 via-primary/5 to-transparent">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-16 sm:py-24">
          <div className="text-center mb-10">
            <h1 className="text-4xl sm:text-5xl font-bold tracking-tight text-foreground">
              智能飞行，从{" "}
              <span className="text-primary">SkyBooker</span> 开始
            </h1>
            <p className="mt-4 text-lg text-muted-foreground max-w-2xl mx-auto">
              AI 驱动的航班搜索与预订平台 —— 用自然语言描述需求，秒级获取最优航班方案
            </p>
          </div>
          <div className="max-w-3xl mx-auto">
            <FlightSearchCard />
          </div>
        </div>
      </section>

      {/* Hot Routes */}
      <section className="py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <h2 className="text-2xl font-bold text-center mb-8">热门航线</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {HOT_ROUTES.map((route) => (
              <Link
                key={`${route.from}-${route.to}`}
                href={`/flights?departureCity=${route.from}&arrivalCity=${route.to}`}
                className="group rounded-xl border border-slate-200 bg-white p-5 hover:shadow-md hover:border-primary/30 transition-all"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-primary/10">
                      <Plane className="h-4 w-4 text-primary group-hover:-translate-y-0.5 group-hover:translate-x-0.5 transition-transform" />
                    </div>
                    <div>
                      <p className="font-medium">{route.from}</p>
                      <p className="text-xs text-muted-foreground">→ {route.to}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-muted-foreground">起</p>
                    <p className="text-[#f97316] font-bold">¥{route.price}</p>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-16 sm:py-20 bg-slate-50/50">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <h2 className="text-2xl font-bold text-center mb-10">为什么选择 SkyBooker？</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {FEATURES.map((f) => (
              <div key={f.title} className="text-center">
                <div className="inline-flex items-center justify-center h-12 w-12 rounded-xl bg-primary/10 mb-4">
                  <f.icon className="h-6 w-6 text-primary" />
                </div>
                <h3 className="font-semibold mb-2">{f.title}</h3>
                <p className="text-sm text-muted-foreground">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <HomeCTA />
    </UserLayout>
  )
}
