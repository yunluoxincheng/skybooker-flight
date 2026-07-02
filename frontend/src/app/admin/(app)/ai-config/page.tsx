"use client"

import { useEffect, useState } from "react"
import { Cpu, Loader2, Key } from "lucide-react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Skeleton } from "@/components/ui/skeleton"
import * as adminApi from "@/services/adminApi"
import type { LlmConfigVO, LlmConfigDTO } from "@/types/admin"

const ERROR_10022_MSG = "LLM 配置校验失败，请检查配置项；如仍失败请确认后端已配置 AI_CONFIG_ENC_KEY"

export default function AdminAiConfigPage() {
  const [config, setConfig] = useState<LlmConfigVO | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  // Form fields
  const [enabled, setEnabled] = useState(true)
  const [baseUrl, setBaseUrl] = useState("")
  const [model, setModel] = useState("")
  const [apiKey, setApiKey] = useState("")
  const [apiKeyTouched, setApiKeyTouched] = useState(false)
  const [apiKeyConfigured, setApiKeyConfigured] = useState(false)
  const [timeoutMs, setTimeoutMs] = useState("")
  const [maxRetries, setMaxRetries] = useState("")

  // Load config
  useEffect(() => {
    adminApi
      .getLlmConfig()
      .then((c) => {
        setConfig(c)
        setEnabled(c.enabled)
        setBaseUrl(c.baseUrl)
        setModel(c.model)
        // 后端返回脱敏值，不放入可编辑输入框
        setApiKey("")
        setApiKeyConfigured(!!c.apiKey)
        setTimeoutMs(String(c.timeoutMs))
        setMaxRetries(String(c.maxRetries))
      })
      .catch((err) => setError((err as { message?: string }).message || "加载配置失败"))
      .finally(() => setIsLoading(false))
  }, [])

  const handleSave = async () => {
    setIsSaving(true)
    setError(null)
    setSuccess(false)

    const data: LlmConfigDTO = {
      enabled,
      baseUrl: baseUrl.trim(),
      model: model.trim(),
      timeoutMs: timeoutMs === "" ? undefined : Number(timeoutMs),
      maxRetries: maxRetries === "" ? undefined : Number(maxRetries),
    }

    // 仅用户主动修改时才传 apiKey，避免空字符串覆盖
    if (apiKeyTouched && apiKey.trim()) {
      data.apiKey = apiKey.trim()
    }

    try {
      const updated = await adminApi.updateLlmConfig(data)
      setConfig(updated)
      setApiKey("")
      setApiKeyConfigured(!!updated.apiKey)
      setApiKeyTouched(false)
      setSuccess(true)
      setTimeout(() => setSuccess(false), 3000)
    } catch (err) {
      const code = (err as { code?: number }).code
      const message = (err as { message?: string }).message || "保存失败"
      if (code === 10022) {
        setError(ERROR_10022_MSG)
      } else {
        setError(message)
      }
    } finally {
      setIsSaving(false)
    }
  }

  // Loading
  if (isLoading) {
    return (
      <div className="max-w-2xl space-y-6">
        <Skeleton className="h-8 w-48" />
        <Card>
          <CardContent className="p-6 space-y-5">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-9 w-full" />
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="max-w-2xl space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <div className="flex items-center justify-center h-8 w-8 rounded-lg bg-primary/10">
          <Cpu className="h-4 w-4 text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">AI 配置</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {config?.source === "DB" ? "当前使用数据库配置" : "当前使用环境变量（fallback）"}
            {" · "}更新于 {config?.updatedAt ? new Date(config.updatedAt).toLocaleString("zh-CN") : "—"}
          </p>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      )}

      {/* Success */}
      {success && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          配置已保存，无需重启后端即可生效
        </div>
      )}

      {/* Form */}
      <Card>
        <CardContent className="p-6 space-y-5">
          {/* Enabled */}
          <div className="flex items-center gap-3">
            <Checkbox
              checked={enabled}
              onCheckedChange={(v) => setEnabled(Boolean(v))}
              id="ai-enabled"
            />
            <Label htmlFor="ai-enabled" className="text-sm font-medium cursor-pointer">
              启用 AI 助手
            </Label>
          </div>

          {/* baseUrl */}
          <div className="space-y-1.5">
            <Label htmlFor="ai-base-url" className="text-sm font-medium">接口地址</Label>
            <Input
              id="ai-base-url"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="https://api.deepseek.com"
            />
          </div>

          {/* model */}
          <div className="space-y-1.5">
            <Label htmlFor="ai-model" className="text-sm font-medium">模型</Label>
            <Input
              id="ai-model"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="deepseek-v4-flash"
            />
          </div>

          {/* apiKey */}
          <div className="space-y-1.5">
            <Label htmlFor="ai-api-key" className="text-sm font-medium">API Key</Label>
            {apiKeyConfigured && (
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Key className="h-3 w-3" />
                当前已配置 API Key（加密存储，不回显明文）
              </div>
            )}
            <Input
              id="ai-api-key"
              type="password"
              value={apiKey}
              onChange={(e) => {
                setApiKey(e.target.value)
                setApiKeyTouched(true)
              }}
              placeholder="如需更换请输入新密钥，留空不修改"
            />
          </div>

          {/* timeoutMs */}
          <div className="space-y-1.5">
            <Label htmlFor="ai-timeout" className="text-sm font-medium">超时 (毫秒)</Label>
            <Input
              id="ai-timeout"
              type="number"
              value={timeoutMs}
              onChange={(e) => setTimeoutMs(e.target.value)}
              placeholder="8000"
            />
          </div>

          {/* maxRetries */}
          <div className="space-y-1.5">
            <Label htmlFor="ai-max-retries" className="text-sm font-medium">最大重试次数</Label>
            <Input
              id="ai-max-retries"
              type="number"
              value={maxRetries}
              onChange={(e) => setMaxRetries(e.target.value)}
              placeholder="1"
            />
          </div>

          <Button
            onClick={handleSave}
            disabled={isSaving}
            className="w-full"
          >
            {isSaving ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin mr-1" />
                保存中...
              </>
            ) : (
              "保存配置"
            )}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
