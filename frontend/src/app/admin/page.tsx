import { Suspense } from "react"
import { AdminAuthProvider } from "@/contexts/AdminAuthContext"
import { AdminLoginForm } from "@/features/auth/components/AdminLoginForm"

export default function AdminLoginPage() {
  return (
    <AdminAuthProvider>
      <Suspense fallback={null}>
        <AdminLoginForm />
      </Suspense>
    </AdminAuthProvider>
  )
}
