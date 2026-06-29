"use client"

import { useState, useEffect, useCallback } from "react"

/**
 * 倒计时 hook，用于验证码按钮
 * @param seconds 倒计时秒数，默认 60
 * @returns [count, start, reset] — 当前剩余秒数，开始倒计时，重置
 */
export function useCountdown(
  seconds = 60
): [number, () => void, () => void] {
  const [count, setCount] = useState(0)
  const [running, setRunning] = useState(false)

  useEffect(() => {
    if (!running) return

    const timer = setInterval(() => {
      setCount((c) => {
        if (c <= 1) {
          setRunning(false)
          return 0
        }
        return c - 1
      })
    }, 1000)
    return () => clearInterval(timer)
  }, [running])

  const start = useCallback(() => {
    setCount(seconds)
    setRunning(true)
  }, [seconds])

  const reset = useCallback(() => {
    setCount(0)
    setRunning(false)
  }, [])

  return [count, start, reset]
}
