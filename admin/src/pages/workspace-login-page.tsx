import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { LogInIcon, ShieldCheckIcon } from 'lucide-react'
import { toast } from 'sonner'
import { loginWorkspace } from '@/api/workspace'
import { BrandLogo } from '@/components/brand-logo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { getWorkspaceSession, setWorkspaceSession } from '@/lib/auth'

export function WorkspaceLoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [userName, setUserName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (getWorkspaceSession()) {
    return <Navigate to="/workspace/dashboard" replace />
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const session = await loginWorkspace({ userName: userName.trim(), password })
      setWorkspaceSession(session)
      toast.success('已进入兔场工作台')
      const from = (location.state as { from?: Location } | null)?.from?.pathname
      navigate(from?.startsWith('/workspace/') ? from : '/workspace/dashboard', {
        replace: true,
      })
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-secondary px-4 py-8">
      <div className="motion-page flex w-full max-w-md flex-col gap-5">
        <div className="flex items-center justify-center gap-3">
          <BrandLogo className="h-14 w-16" />
          <div>
            <p className="text-base font-semibold">Rabbit Farm</p>
            <p className="text-xs text-muted-foreground">兔场工作台</p>
          </div>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>登录兔场工作台</CardTitle>
            <CardDescription>使用兔场客户端相同的业务账号。</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="workspace-user-name">用户名</FieldLabel>
                  <Input
                    id="workspace-user-name"
                    value={userName}
                    autoComplete="username"
                    required
                    autoFocus
                    onChange={(event) => setUserName(event.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="workspace-password">密码</FieldLabel>
                  <Input
                    id="workspace-password"
                    type="password"
                    value={password}
                    autoComplete="current-password"
                    required
                    onChange={(event) => setPassword(event.target.value)}
                  />
                </Field>
              </FieldGroup>
              <Button type="submit" disabled={submitting}>
                {submitting ? <Spinner data-icon="inline-start" /> : <LogInIcon data-icon="inline-start" />}
                登录
              </Button>
            </form>
          </CardContent>
        </Card>
        <Button variant="ghost" asChild>
          <Link to="/login">
            <ShieldCheckIcon data-icon="inline-start" />
            平台管理员登录
          </Link>
        </Button>
      </div>
    </main>
  )
}
