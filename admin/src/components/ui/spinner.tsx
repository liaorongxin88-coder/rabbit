import { LoaderCircleIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

function Spinner({ className }: { className?: string }) {
  return (
    <LoaderCircleIcon
      data-icon="inline-start"
      className={cn('animate-spin', className)}
      aria-hidden="true"
    />
  )
}

export { Spinner }
