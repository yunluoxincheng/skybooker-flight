"use client"

import { useMemo, useState } from "react"
import { ChevronsUpDown, Loader2 } from "lucide-react"

import { Command, CommandEmpty, CommandInput, CommandItem, CommandList } from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "@/lib/utils"

export type ComboboxOption = object

export interface ComboboxProps<T extends ComboboxOption> {
  options: T[]
  value?: string | number | null
  onValueChange?: (value: string) => void
  placeholder?: string
  searchPlaceholder?: string
  emptyMessage?: string
  getDisplayValue: (option: T) => string
  getSearchFields: (option: T) => string[]
  keyField?: string
  disabled?: boolean
  loading?: boolean
  className?: string
}

function Combobox<T extends ComboboxOption>({
  options,
  value,
  onValueChange,
  placeholder = "请选择",
  searchPlaceholder = "搜索选项...",
  emptyMessage = "暂无匹配结果",
  getDisplayValue,
  getSearchFields,
  keyField = "id",
  disabled = false,
  loading = false,
  className,
}: ComboboxProps<T>) {
  const [open, setOpen] = useState(false)

  const normalizedValue = value == null ? null : String(value)
  const getOptionKey = (option: T) => String((option as Record<string, unknown>)[keyField])

  const selectedOption = useMemo(
    () => options.find((option) => getOptionKey(option) === normalizedValue),
    [keyField, normalizedValue, options]
  )

  const searchIndex = useMemo(
    () =>
      Object.fromEntries(
        options.map((option) => [
          getOptionKey(option),
          getSearchFields(option)
            .join(" ")
            .toLowerCase(),
        ])
      ),
    [getSearchFields, keyField, options]
  )

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        render={
          <button
            type="button"
            role="combobox"
            aria-expanded={open}
            disabled={disabled}
            className={cn(
              "flex h-8 w-full items-center justify-between gap-1.5 rounded-lg border border-input bg-transparent py-2 pr-2 pl-2.5 text-sm transition-colors outline-none select-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30 dark:hover:bg-input/50",
              "data-[placeholder=true]:text-muted-foreground",
              className
            )}
            data-placeholder={!selectedOption}
          >
            <span className="min-w-0 flex-1 text-left">
              <span className="block truncate">
                {selectedOption ? getDisplayValue(selectedOption) : placeholder}
              </span>
            </span>
            <ChevronsUpDown className="size-4 shrink-0 text-muted-foreground" />
          </button>
        }
      />
      <PopoverContent align="start" className="w-[--anchor-width] p-0">
        <Command
          filter={(optionValue, search) => {
            const normalizedSearch = search.trim().toLowerCase()
            if (!normalizedSearch) return 1

            return searchIndex[optionValue]?.includes(normalizedSearch) ? 1 : 0
          }}
        >
          <CommandInput placeholder={searchPlaceholder} />
          <CommandList>
            {loading ? (
              <div className="flex items-center justify-center gap-2 px-3 py-6 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" />
                加载中...
              </div>
            ) : (
              <>
                <CommandEmpty>{emptyMessage}</CommandEmpty>
                {options.map((option) => {
                  const optionValue = getOptionKey(option)

                  return (
                    <CommandItem
                      key={optionValue}
                      value={optionValue}
                      data-checked={optionValue === normalizedValue}
                      onSelect={(selectedValue) => {
                        onValueChange?.(selectedValue)
                        setOpen(false)
                      }}
                    >
                      <span className="min-w-0 flex-1 truncate">{getDisplayValue(option)}</span>
                    </CommandItem>
                  )
                })}
              </>
            )}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

export { Combobox }
