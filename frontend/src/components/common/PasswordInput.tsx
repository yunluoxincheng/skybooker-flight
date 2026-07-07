"use client"

import { Eye, EyeOff } from "lucide-react"
import type { UseFormRegisterReturn } from "react-hook-form"
import { InputGroup, InputGroupButton, InputGroupInput } from "@/components/ui/input-group"
import { usePasswordVisibility } from "@/hooks/usePasswordVisibility"
import { cn } from "@/lib/utils"

interface PasswordInputProps {
  id: string
  placeholder?: string
  register: UseFormRegisterReturn
  error?: string
  showCapsLock?: boolean
  ariaLabelShow?: string
  ariaLabelHide?: string
  autoComplete?: string
  disabled?: boolean
  className?: string
}

export function PasswordInput({
  id,
  placeholder,
  register,
  error,
  showCapsLock = false,
  ariaLabelShow = "显示密码",
  ariaLabelHide = "隐藏密码",
  autoComplete,
  disabled,
  className,
}: PasswordInputProps) {
  const { showPassword, isCapsLock, updateCapsLockState, togglePasswordVisibility } =
    usePasswordVisibility()

  return (
    <>
      <InputGroup className={className}>
        <InputGroupInput
          id={id}
          type={showPassword ? "text" : "password"}
          placeholder={placeholder}
          autoComplete={autoComplete}
          disabled={disabled}
          aria-invalid={error ? "true" : "false"}
          {...register}
          onKeyDown={updateCapsLockState}
          onKeyUp={updateCapsLockState}
        />
        <InputGroupButton
          size="icon-xs"
          variant="ghost"
          type="button"
          disabled={disabled}
          aria-label={showPassword ? ariaLabelHide : ariaLabelShow}
          onClick={togglePasswordVisibility}
        >
          {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </InputGroupButton>
      </InputGroup>
      {showCapsLock && isCapsLock && (
        <p className="text-xs text-amber-600 dark:text-amber-400">大写锁定已开启</p>
      )}
      {error && <p className={cn("text-xs text-destructive")}>{error}</p>}
    </>
  )
}
