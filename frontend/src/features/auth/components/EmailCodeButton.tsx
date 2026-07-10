"use client"

import { Button } from "@/components/ui/button"
import { useCountdown } from "@/hooks/useCountdown"
import { useSendCode } from "../hooks/useAuth"
import type { EmailCodeScene } from "@/types/auth"

interface EmailCodeButtonProps {
  email: string
  scene: EmailCodeScene
  disabled?: boolean
}

export function EmailCodeButton({ email, scene, disabled }: EmailCodeButtonProps) {
  const [count, start] = useCountdown(60)
  const { sendCode, sending } = useSendCode()
  const counting = count > 0

  const handleClick = async () => {
    try {
      await sendCode(email, scene)
      start()
    } catch {
      // error handled in hook
    }
  }

  return (
    <Button
      type="button"
      variant="outline"
      size="sm"
      disabled={disabled || counting || sending || !email}
      onClick={handleClick}
      className="shrink-0"
    >
      {sending ? "发送中..." : counting ? `${count}秒后重试` : "发送验证码"}
    </Button>
  )
}
