import { cn } from '@/lib/utils'

export function BrandLogo({ className }: { className?: string }) {
  return (
    <img
      src="/rabbit-logo.png"
      alt=""
      aria-hidden="true"
      className={cn('block shrink-0 object-contain', className)}
    />
  )
}
