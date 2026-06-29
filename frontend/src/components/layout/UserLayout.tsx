import type { ReactNode } from "react"
import { Header } from "./Header"
import { Footer } from "./Footer"

export function UserLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Header />
      <main className="flex-1">{children}</main>
      <Footer />
    </>
  )
}
