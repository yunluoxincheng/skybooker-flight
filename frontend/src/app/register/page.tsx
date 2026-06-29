import { AuthLayout } from "@/features/auth/components/AuthLayout"
import { RegisterForm } from "@/features/auth/components/RegisterForm"

export default function RegisterPage() {
  return (
    <AuthLayout title="创建账号" subtitle="注册以开始使用智能购票服务">
      <RegisterForm />
    </AuthLayout>
  )
}
