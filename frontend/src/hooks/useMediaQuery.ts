"use client"

import { useSyncExternalStore } from "react"

/**
 * 响应式断点检测 hook
 * @param query CSS media query 字符串，如 "(min-width: 768px)"
 * @returns 是否匹配
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = (callback: () => void) => {
    const mql = window.matchMedia(query)
    mql.addEventListener("change", callback)
    return () => mql.removeEventListener("change", callback)
  }

  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(query).matches,
  )
}

/** 便捷断点：>= 768px */
export function useIsTablet(): boolean {
  return useMediaQuery("(min-width: 768px)")
}

/** 便捷断点：>= 1024px */
export function useIsDesktop(): boolean {
  return useMediaQuery("(min-width: 1024px)")
}
