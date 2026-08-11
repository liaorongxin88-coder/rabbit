import { useEffect, useState } from 'react'
import { SendIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import type { SmsCodeDelivery } from '@/types/api'

export function SmsCodeField({
  id,
  label,
  value,
  disabled,
  successMessage = '验证码已发送',
  onChange,
  onSend,
}: {
  id: string
  label: string
  value: string
  disabled?: boolean
  successMessage?: string
  onChange: (value: string) => void
  onSend: () => Promise<SmsCodeDelivery>
}) {
  const [sending, setSending] = useState(false)
  const [remainingSeconds, setRemainingSeconds] = useState(0)

  useEffect(() => {
    if (remainingSeconds <= 0) {
      return
    }
    const timer = window.setTimeout(
      () => setRemainingSeconds((current) => Math.max(0, current - 1)),
      1000,
    )
    return () => window.clearTimeout(timer)
  }, [remainingSeconds])

  async function handleSend() {
    setSending(true)
    try {
      const delivery = await onSend()
      setRemainingSeconds(delivery.retryAfterSeconds)
      toast.success(successMessage)
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSending(false)
    }
  }

  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <div className="grid grid-cols-[minmax(0,1fr)_7.5rem] gap-2">
        <Input
          id={id}
          value={value}
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          onChange={(event) => onChange(event.target.value.replace(/\D/g, ''))}
        />
        <Button
          type="button"
          variant="outline"
          disabled={disabled || sending || remainingSeconds > 0}
          onClick={() => void handleSend()}
        >
          {sending ? <Spinner data-icon="inline-start" /> : <SendIcon data-icon="inline-start" />}
          {remainingSeconds > 0 ? `${remainingSeconds}s` : '获取验证码'}
        </Button>
      </div>
    </Field>
  )
}
