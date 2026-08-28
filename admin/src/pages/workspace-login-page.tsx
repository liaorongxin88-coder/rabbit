import { useCallback, useEffect, useState } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  KeyRoundIcon,
  LogInIcon,
  RefreshCwIcon,
  ShieldCheckIcon,
} from "lucide-react";
import { toast } from "sonner";
import {
  getWorkspaceImageCaptcha,
  loginWorkspace,
  resetWorkspacePasswordBySms,
  sendWorkspaceSmsCode,
} from "@/api/workspace";
import { BrandLogo } from "@/components/brand-logo";
import { SmsCodeField } from "@/components/sms-code-field";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { getWorkspaceSession, setWorkspaceSession } from "@/lib/auth";
import { ApiError } from "@/lib/request";
import type { ImageCaptcha } from "@/types/api";

export function WorkspaceLoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);
  const [captcha, setCaptcha] = useState<ImageCaptcha | null>(null);
  const [captchaCode, setCaptchaCode] = useState("");
  const [captchaLoading, setCaptchaLoading] = useState(true);
  const [captchaError, setCaptchaError] = useState<string | null>(null);
  // 501 表示图片验证码在本部署未启用，后端也不会校验，此时必须放行登录；
  // 503 才是服务暂时不可用，那种情况仍然拦住。
  const [captchaNotRequired, setCaptchaNotRequired] = useState(false);

  const loadCaptcha = useCallback(async () => {
    setCaptchaLoading(true);
    setCaptchaError(null);
    try {
      setCaptcha(await getWorkspaceImageCaptcha());
      setCaptchaCode("");
      setCaptchaNotRequired(false);
    } catch (error) {
      setCaptcha(null);
      const notRequired = error instanceof ApiError && error.code === 501;
      setCaptchaNotRequired(notRequired);
      setCaptchaError(
        notRequired
          ? null
          : error instanceof Error
            ? error.message
            : "图片验证码加载失败，请刷新重试",
      );
    } finally {
      setCaptchaLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCaptcha();
  }, [loadCaptcha]);

  if (getWorkspaceSession()) {
    return <Navigate to="/workspace/dashboard" replace />;
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!captcha && !captchaNotRequired) {
      setCaptchaError("图片验证码尚未准备好，请刷新后重试");
      return;
    }
    setSubmitting(true);
    try {
      const session = await loginWorkspace({
        userName: userName.trim(),
        password,
        captchaId: captcha?.captchaId ?? "",
        captchaCode: captcha ? captchaCode.trim().toUpperCase() : "",
      });
      setWorkspaceSession(session);
      toast.success("已进入兔场工作台");
      const from = (location.state as { from?: Location } | null)?.from
        ?.pathname;
      navigate(
        from?.startsWith("/workspace/") ? from : "/workspace/dashboard",
        {
          replace: true,
        },
      );
    } catch {
      // The shared request layer reports the business error.
    } finally {
      setSubmitting(false);
      void loadCaptcha();
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
                {captchaNotRequired ? null : (
                  <Field>
                    <FieldLabel htmlFor="workspace-captcha-code">
                      图片验证码
                    </FieldLabel>
                  <div className="flex items-center gap-2">
                    <div className="flex h-10 min-w-33 items-center justify-center overflow-hidden rounded-md border bg-background px-1">
                      {captcha ? (
                        <img
                          className="h-9 w-32 object-contain"
                          src={`data:image/png;base64,${captcha.imageBase64}`}
                          alt="图片验证码"
                        />
                      ) : (
                        <span className="text-xs text-muted-foreground">
                          {captchaLoading ? "加载中" : "加载失败"}
                        </span>
                      )}
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      disabled={captchaLoading || submitting}
                      aria-label="刷新图片验证码"
                      title="刷新图片验证码"
                      onClick={() => void loadCaptcha()}
                    >
                      <RefreshCwIcon data-icon="inline" />
                    </Button>
                    <Input
                      id="workspace-captcha-code"
                      className="min-w-0 flex-1"
                      value={captchaCode}
                      inputMode="text"
                      autoComplete="off"
                      maxLength={4}
                      required
                      disabled={!captcha || captchaLoading || submitting}
                      placeholder="输入图中字符"
                      onChange={(event) =>
                        setCaptchaCode(event.target.value.toUpperCase())
                      }
                    />
                  </div>
                    {captchaError ? (
                      <p className="text-xs text-destructive">{captchaError}</p>
                    ) : null}
                  </Field>
                )}
              </FieldGroup>
              <Button type="submit" disabled={submitting}>
                {submitting ? (
                  <Spinner data-icon="inline-start" />
                ) : (
                  <LogInIcon data-icon="inline-start" />
                )}
                登录
              </Button>
              <Button
                type="button"
                variant="link"
                onClick={() => setResetOpen(true)}
              >
                <KeyRoundIcon data-icon="inline-start" />
                忘记密码
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
        <ResetPasswordDialog open={resetOpen} onOpenChange={setResetOpen} />
      </div>
    </main>
  );
}

function ResetPasswordDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open ? (
        <ResetPasswordForm onSuccess={() => onOpenChange(false)} />
      ) : null}
    </Dialog>
  );
}

function ResetPasswordForm({ onSuccess }: { onSuccess: () => void }) {
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const validPhone = /^1[3-9]\d{9}$/.test(phone);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      toast.error("两次输入的新密码不一致");
      return;
    }
    setSaving(true);
    try {
      await resetWorkspacePasswordBySms({ phone, code, newPassword });
      setSaving(false);
      toast.success("密码已重置，请使用新密码登录");
      onSuccess();
    } catch {
      setSaving(false);
    }
  }

  return (
    <DialogContent>
      <DialogHeader>
        <DialogTitle>重置密码</DialogTitle>
        <DialogDescription>验证已绑定手机号后设置新密码。</DialogDescription>
      </DialogHeader>
      <form className="contents" onSubmit={handleSubmit}>
        <FieldGroup className="overflow-y-auto py-1">
          <Field>
            <FieldLabel htmlFor="workspace-reset-phone">
              已绑定手机号
            </FieldLabel>
            <Input
              id="workspace-reset-phone"
              value={phone}
              inputMode="tel"
              autoComplete="tel"
              pattern="1[3-9][0-9]{9}"
              maxLength={11}
              required
              onChange={(event) =>
                setPhone(event.target.value.replace(/\D/g, ""))
              }
            />
          </Field>
          <SmsCodeField
            id="workspace-reset-code"
            label="验证码"
            value={code}
            disabled={!validPhone}
            onChange={setCode}
            onSend={() => sendWorkspaceSmsCode(phone, "RESET_PASSWORD")}
          />
          <Field>
            <FieldLabel htmlFor="workspace-reset-password">新密码</FieldLabel>
            <Input
              id="workspace-reset-password"
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
            <FieldLabel htmlFor="workspace-reset-confirm-password">
              确认新密码
            </FieldLabel>
            <Input
              id="workspace-reset-confirm-password"
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
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={saving}
            onClick={onSuccess}
          >
            取消
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <KeyRoundIcon data-icon="inline-start" />
            )}
            重置密码
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  );
}
