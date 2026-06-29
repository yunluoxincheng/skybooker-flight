import { AdminAuthProvider } from "@/contexts/AdminAuthContext"
import { AdminLoginForm } from "@/features/auth/components/AdminLoginForm"

export default function AdminLoginPage() {
  return (
    <AdminAuthProvider>
      <AdminLoginForm />
    </AdminAuthProvider>
  )
}
