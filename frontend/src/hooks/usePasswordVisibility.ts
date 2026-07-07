"use client"

import { useState, type KeyboardEvent, type MouseEvent } from "react"

/**
 * 管理密码输入框的显隐状态，并在切换时尽量保持输入焦点与光标位置。
 */
export function usePasswordVisibility() {
  const [showPassword, setShowPassword] = useState(false)
  const [isCapsLock, setIsCapsLock] = useState(false)

  const updateCapsLockState = (e: KeyboardEvent<HTMLInputElement>) => {
    setIsCapsLock(e.getModifierState("CapsLock"))
  }

  const togglePasswordVisibility = (e: MouseEvent<HTMLButtonElement>) => {
    e.preventDefault()

    const input = e.currentTarget.closest('[data-slot="input-group"]')?.querySelector("input") ?? null
    const selectionStart = input?.selectionStart ?? null
    const selectionEnd = input?.selectionEnd ?? null

    setShowPassword((visible) => !visible)

    requestAnimationFrame(() => {
      if (!input) {
        return
      }

      input.focus({ preventScroll: true })

      if (selectionStart !== null && selectionEnd !== null) {
        input.setSelectionRange(selectionStart, selectionEnd)
      }
    })
  }

  return {
    showPassword,
    isCapsLock,
    updateCapsLockState,
    togglePasswordVisibility,
  }
}
