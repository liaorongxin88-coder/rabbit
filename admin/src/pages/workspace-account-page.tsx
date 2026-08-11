import { useCallback, useEffect, useState } from 'react'
import {
  KeyRoundIcon,
  PhoneIcon,
  RefreshCwIcon,
  SaveIcon,
  ShieldCheckIcon,
  UserRoundIcon,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  getWorkspaceProfile,
  sendWorkspaceSmsCode,
  updateWorkspacePassword,
  updateWorkspacePhone,
  updateWorkspaceUserName,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { SmsCodeField } from '@/components/sms-code-field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { getWorkspaceSession, setWorkspaceSession } from '@/lib/auth'
import type { WorkspaceUserProfile } from '@/types/api'

const mainlandPhonePattern = /^1[3-9]\d{9}$/

export function WorkspaceAccountPage() {
  const [profile, setProfile] = useState<WorkspaceUserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [userName, setUserName] = useState('')
  const [savingName, setSavingName] = useState(false)
  const [currentPasswordForPhone, setCurrentPasswordForPhone] = useState('')
  const [currentPhone, setCurrentPhone] = useState('')
  const [currentPhoneCode, setCurrentPhoneCode] = useState('')
  const [newPhone, setNewPhone] = useState('')
  const [newPhoneCode, setNewPhoneCode] = useState('')
  const [savingPhone, setSavingPhone] = useState(false)
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)

  const applyProfile = useCallback((nextProfile: WorkspaceUserProfile) => {
    setProfile(nextProfile)
    setUserName(nextProfile.userName)
    const session = getWorkspaceSession()
    if (session) {
      setWorkspaceSession({
        ...session,
        userName: nextProfile.userName,
        phoneBound: nextProfile.phoneBound,
        maskedPhone: nextProfile.maskedPhone,
        hasPassword: nextProfile.hasPassword,
        permissions: nextProfile.permissions,
      })
    }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setLoadFailed(false)
    try {
      applyProfile(await getWorkspaceProfile())
    } catch {
      setLoadFailed(true)
    } finally {
      setLoading(false)
    }
  }, [applyProfile])

  useEffect(() => {
    void load()
  }, [load])

  async function saveUserName(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSavingName(true)
    try {
      const updated = await updateWorkspaceUserName(userName.trim())
      applyProfile(updated)
      toast.success('用户名已保存')
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSavingName(false)
    }
  }

  async function savePhone(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profile || !mainlandPhonePattern.test(newPhone)) {
      toast.error('请输入有效的中国大陆手机号')
      return
    }
    setSavingPhone(true)
    try {
      const updated = await updateWorkspacePhone({
        phone: newPhone,
        code: newPhoneCode,
        currentPassword: currentPasswordForPhone || undefined,
        currentPhone: currentPhone || undefined,
        currentPhoneCode: currentPhoneCode || undefined,
      })
      applyProfile(updated)
      setCurrentPasswordForPhone('')
      setCurrentPhone('')
      setCurrentPhoneCode('')
      setNewPhone('')
      setNewPhoneCode('')
      toast.success(profile.phoneBound ? '手机号已更换' : '手机号已绑定')
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSavingPhone(false)
    }
  }

  async function savePassword(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profile) {
      return
    }
    if (newPassword !== confirmPassword) {
      toast.error('两次输入的新密码不一致')
      return
    }
    setSavingPassword(true)
    try {
      await updateWorkspacePassword({
        oldPassword: profile.hasPassword ? oldPassword : undefined,
        newPassword,
      })
      const wasInitialized = profile.hasPassword
      applyProfile(await getWorkspaceProfile())
      setOldPassword('')
      setNewPassword('')
      setConfirmPassword('')
      toast.success(wasInitialized ? '密码已修改' : '密码已设置')
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <>
      <PageHeader
        title="账号安全"
        description="维护登录身份、手机号和密码。"
        actions={
          <Button variant="outline" disabled={loading} onClick={() => void load()}>
            <RefreshCwIcon data-icon="inline-start" />
            刷新
          </Button>
        }
      />

      {loading && !profile ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : loadFailed || !profile ? (
        <Empty>
          <ShieldCheckIcon aria-hidden="true" />
          <EmptyTitle>账号资料加载失败</EmptyTitle>
          <EmptyDescription>刷新后重试。</EmptyDescription>
        </Empty>
      ) : (
        <div className="grid items-start gap-4 lg:grid-cols-2">
          <div className="grid gap-4">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <UserRoundIcon aria-hidden="true" />
                  基本资料
                </CardTitle>
                <CardDescription>用户 ID {profile.userId}</CardDescription>
              </CardHeader>
              <CardContent>
                <form className="grid gap-4" onSubmit={saveUserName}>
                  <Field>
                    <FieldLabel htmlFor="workspace-account-user-name">用户名</FieldLabel>
                    <Input
                      id="workspace-account-user-name"
                      value={userName}
                      maxLength={64}
                      required
                      autoComplete="username"
                      onChange={(event) => setUserName(event.target.value)}
                    />
                  </Field>
                  <Button className="w-fit" type="submit" disabled={savingName}>
                    {savingName ? <Spinner data-icon="inline-start" /> : <SaveIcon data-icon="inline-start" />}
                    保存用户名
                  </Button>
                </form>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <KeyRoundIcon aria-hidden="true" />
                  {profile.hasPassword ? '登录密码' : '设置登录密码'}
                </CardTitle>
                <CardDescription>修改已有密码时必须验证当前密码。</CardDescription>
              </CardHeader>
              <CardContent>
                <form className="grid gap-4" onSubmit={savePassword}>
                  <FieldGroup>
                    {profile.hasPassword ? (
                      <Field>
                        <FieldLabel htmlFor="workspace-old-password">当前密码</FieldLabel>
                        <Input
                          id="workspace-old-password"
                          type="password"
                          value={oldPassword}
                          minLength={6}
                          maxLength={32}
                          autoComplete="current-password"
                          required
                          onChange={(event) => setOldPassword(event.target.value)}
                        />
                      </Field>
                    ) : null}
                    <Field>
                      <FieldLabel htmlFor="workspace-new-password">新密码</FieldLabel>
                      <Input
                        id="workspace-new-password"
                        type="password"
                        value={newPassword}
                        minLength={6}
                        maxLength={32}
                        autoComplete="new-password"
                        required
                        onChange={(event) => setNewPassword(event.target.value)}
                      />
                    </Field>
                    <Field>
                      <FieldLabel htmlFor="workspace-confirm-password">确认新密码</FieldLabel>
                      <Input
                        id="workspace-confirm-password"
                        type="password"
                        value={confirmPassword}
                        minLength={6}
                        maxLength={32}
                        autoComplete="new-password"
                        required
                        onChange={(event) => setConfirmPassword(event.target.value)}
                      />
                    </Field>
                  </FieldGroup>
                  <Button className="w-fit" type="submit" variant="outline" disabled={savingPassword}>
                    {savingPassword ? <Spinner data-icon="inline-start" /> : <KeyRoundIcon data-icon="inline-start" />}
                    {profile.hasPassword ? '修改密码' : '设置密码'}
                  </Button>
                </form>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <PhoneIcon aria-hidden="true" />
                手机号
              </CardTitle>
              <CardDescription>
                {profile.phoneBound
                  ? `当前绑定：${profile.maskedPhone || '已绑定'}`
                  : '绑定后可使用短信登录和找回密码。'}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form className="grid gap-4" onSubmit={savePhone}>
                <FieldGroup>
                  {profile.phoneBound && profile.hasPassword ? (
                    <Field>
                      <FieldLabel htmlFor="workspace-phone-current-password">当前密码</FieldLabel>
                      <Input
                        id="workspace-phone-current-password"
                        type="password"
                        value={currentPasswordForPhone}
                        minLength={6}
                        maxLength={32}
                        autoComplete="current-password"
                        required
                        onChange={(event) => setCurrentPasswordForPhone(event.target.value)}
                      />
                      <FieldDescription>用于确认本次更换手机号操作。</FieldDescription>
                    </Field>
                  ) : null}
                  {profile.phoneBound && !profile.hasPassword ? (
                    <>
                      <Field>
                        <FieldLabel htmlFor="workspace-current-phone">原手机号</FieldLabel>
                        <Input
                          id="workspace-current-phone"
                          value={currentPhone}
                          inputMode="tel"
                          autoComplete="tel"
                          pattern="1[3-9][0-9]{9}"
                          maxLength={11}
                          required
                          onChange={(event) => setCurrentPhone(event.target.value.replace(/\D/g, ''))}
                        />
                      </Field>
                      <SmsCodeField
                        id="workspace-current-phone-code"
                        label="原手机号验证码"
                        value={currentPhoneCode}
                        disabled={!mainlandPhonePattern.test(currentPhone)}
                        successMessage="原手机号验证码已发送"
                        onChange={setCurrentPhoneCode}
                        onSend={() => sendWorkspaceSmsCode(currentPhone, 'VERIFY_CURRENT_PHONE')}
                      />
                    </>
                  ) : null}
                  <Field>
                    <FieldLabel htmlFor="workspace-new-phone">新手机号</FieldLabel>
                    <Input
                      id="workspace-new-phone"
                      value={newPhone}
                      inputMode="tel"
                      autoComplete="tel"
                      pattern="1[3-9][0-9]{9}"
                      maxLength={11}
                      required
                      onChange={(event) => setNewPhone(event.target.value.replace(/\D/g, ''))}
                    />
                  </Field>
                  <SmsCodeField
                    id="workspace-new-phone-code"
                    label="新手机号验证码"
                    value={newPhoneCode}
                    disabled={!mainlandPhonePattern.test(newPhone)}
                    successMessage="新手机号验证码已发送"
                    onChange={setNewPhoneCode}
                    onSend={() => sendWorkspaceSmsCode(newPhone, 'BIND_PHONE')}
                  />
                </FieldGroup>
                <Button className="w-fit" type="submit" disabled={savingPhone}>
                  {savingPhone ? <Spinner data-icon="inline-start" /> : <PhoneIcon data-icon="inline-start" />}
                  {profile.phoneBound ? '更换手机号' : '绑定手机号'}
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>
      )}
    </>
  )
}
