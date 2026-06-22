interface FlightPriceTagProps {
  price: number
  className?: string
}

export function FlightPriceTag({ price, className }: FlightPriceTagProps) {
  return (
    <span className={`text-[#f97316] font-bold ${className || ""}`}>
      ¥{price.toLocaleString()}
    </span>
  )
}
