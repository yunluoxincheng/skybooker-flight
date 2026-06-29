import Link from "next/link"
import { Plane } from "lucide-react"

export function Footer() {
  return (
    <footer className="border-t border-slate-200 bg-white mt-auto">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Plane className="h-4 w-4" />
            <span>SkyBooker 云航智订 &copy; {new Date().getFullYear()}</span>
          </div>
          <div className="flex items-center gap-6 text-sm text-muted-foreground">
            <Link href="/flights" className="hover:text-foreground transition-colors">
              航班查询
            </Link>
            <Link href="/ai-assistant" className="hover:text-foreground transition-colors">
              AI 助手
            </Link>
          </div>
        </div>
      </div>
    </footer>
  )
}
