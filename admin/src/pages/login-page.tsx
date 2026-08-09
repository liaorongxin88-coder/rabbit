import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { RabbitIcon, UserCogIcon, UsersIcon, WarehouseIcon, type LucideIcon } from 'lucide-react'
import { loginAdmin } from '@/api/admin'
import { BrandLogo } from '@/components/brand-logo'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { getSession, setSession } from '@/lib/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [userName, setUserName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const session = getSession()

  if (session) {
    return <Navigate to="/dashboard" replace />
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    try {
      const nextSession = await loginAdmin({ userName, password })
      setSession(nextSession)
      toast.success('登录成功')
      const from = (location.state as { from?: Location } | null)?.from?.pathname
      navigate(from || '/dashboard', { replace: true })
    } catch {
      // The shared request layer already shows the actionable error toast.
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="min-h-screen bg-secondary px-4 py-8">
      <div className="motion-page mx-auto grid min-h-[calc(100vh-4rem)] w-full max-w-5xl items-center gap-6 lg:grid-cols-[minmax(0,1fr)_420px]">
        <section className="flex flex-col gap-5">
          <div className="flex items-center gap-3">
            <BrandLogo className="h-14 w-16" />
            <div>
              <p className="text-sm font-semibold">Rabbit SaaS</p>
              <p className="text-xs text-muted-foreground">平台管理端</p>
            </div>
          </div>
          <div className="max-w-2xl">
            <h1 className="text-2xl font-semibold tracking-normal sm:text-3xl">
              兔场、业务用户和运行状态的统一入口
            </h1>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">
              平台管理员在这里维护兔场状态、用户访问和平台账号权限。
            </p>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <LoginSignal icon={WarehouseIcon} title="兔场运营" text="查看兔场状态、成员和业务规模" />
            <LoginSignal icon={UsersIcon} title="业务用户" text="查看手机号绑定和兔场关联" />
            <LoginSignal icon={UserCogIcon} title="平台权限" text="维护管理端账号和角色" />
          </div>
        </section>

        <Card>
          <CardHeader>
            <CardTitle className="text-xl">Rabbit SaaS 管理端</CardTitle>
            <CardDescription>使用平台管理员账号登录。</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-5" onSubmit={handleSubmit}>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="user-name">用户名</FieldLabel>
                  <Input
                    id="user-name"
                    value={userName}
                    autoComplete="username"
                    placeholder="请输入用户名"
                    required
                    autoFocus
                    onChange={(event) => setUserName(event.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="password">密码</FieldLabel>
                  <Input
                    id="password"
                    type="password"
                    value={password}
                    autoComplete="current-password"
                    placeholder="请输入密码"
                    required
                    onChange={(event) => setPassword(event.target.value)}
                  />
                </Field>
              </FieldGroup>
              <Button type="submit" disabled={submitting}>
                {submitting ? <Spinner /> : null}
                登录
              </Button>
              <Button type="button" variant="ghost" asChild>
                <Link to="/workspace/login">
                  <RabbitIcon data-icon="inline-start" />
                  兔场工作台登录
                </Link>
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </main>
  )
}

function LoginSignal({
  icon: Icon,
  title,
  text,
}: {
  icon: LucideIcon
  title: string
  text: string
}) {
  return (
    <div className="rounded-lg border bg-background p-4">
      <Icon aria-hidden="true" />
      <p className="mt-3 text-sm font-medium">{title}</p>
      <p className="mt-1 text-xs leading-5 text-muted-foreground">{text}</p>
    </div>
  )
}
