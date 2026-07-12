"use client"
import Link from "next/link"
import { UserLayout } from "@/components/layout/UserLayout"
import { Button } from "@/components/ui/button"
export default function LegacyConnectingBookingPage() { return <UserLayout><main className="mx-auto max-w-lg px-4 py-16 text-center"><h1 className="text-xl font-semibold">请从联程详情页开始预订</h1><p className="mt-2 text-sm text-muted-foreground">旧版自由组合航段入口已停用，以确保所选航段属于有效且已上架的联程方案。</p><Button className="mt-6" render={<Link href="/flights">重新搜索航班</Link>} nativeButton={false}/></main></UserLayout> }
