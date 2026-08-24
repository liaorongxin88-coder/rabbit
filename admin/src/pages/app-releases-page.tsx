import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  DownloadIcon,
  PenLineIcon,
  PlusIcon,
  SearchIcon,
  SmartphoneIcon,
} from 'lucide-react'
import {
  createAppRelease,
  downloadAppReleaseApk,
  listAppReleases,
  publishAppRelease,
  revokeAppRelease,
  updateAppRelease,
} from '@/api/app-releases'
import { PageHeader } from '@/components/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldDescription, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import { hasPermission } from '@/lib/permissions'
import type { AdminSession, AppRelease, AppReleaseChannel, AppReleaseStatus, PageResult } from '@/types/api'

const PAGE_SIZE = 20
const MAX_APK_BYTES = 157286400
const MAX_NOTES_LENGTH = 2000
const CHANNEL_LABELS: Record<AppReleaseChannel, string> = {
  dev: '开发',
  test: '测试',
  prod: '正式',
}

export function AppReleasesPage({ session }: { session: AdminSession }) {
  const [channel, setChannel] = useState<AppReleaseChannel | 'ALL'>('ALL')
  const [status, setStatus] = useState<AppReleaseStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AppRelease> | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<AppRelease | null>(null)
  const [pendingAction, setPendingAction] = useState<{
    type: 'publish' | 'revoke'
    release: AppRelease
  } | null>(null)
  const [acting, setActing] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const requestSeq = useRef(0)

  const canAdd = hasPermission(session, 'platform:app-releases:add')
  const canEdit = hasPermission(session, 'platform:app-releases:edit')
  const canQuery = hasPermission(session, 'platform:app-releases:query')
  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? PAGE_SIZE))),
    [data],
  )
  const hasActiveFilters = channel !== 'ALL' || status !== 'ALL'

  const load = useCallback(async () => {
    const seq = ++requestSeq.current
    setLoading(true)
    setLoadError(false)
    try {
      const result = await listAppReleases({
        pageNum: page,
        pageSize: PAGE_SIZE,
        channel,
        status,
      })
      if (seq !== requestSeq.current) return
      if (result.items.length === 0 && result.total > 0 && page > 1) {
        setPage(1)
        return
      }
      setData(result)
    } catch {
      if (seq !== requestSeq.current) return
      setData(null)
      setLoadError(true)
    } finally {
      if (seq === requestSeq.current) {
        setLoading(false)
      }
    }
  }, [channel, page, status])

  useEffect(() => {
    void load()
  }, [load])

  function handleReset() {
    setChannel('ALL')
    setStatus('ALL')
    setPage(1)
  }

  async function handleConfirmAction() {
    if (!pendingAction) return
    setActing(true)
    try {
      if (pendingAction.type === 'publish') {
        await publishAppRelease(pendingAction.release.id)
        toast.success(`已发布 ${pendingAction.release.versionName}`)
      } else {
        await revokeAppRelease(pendingAction.release.id)
        toast.success(
          pendingAction.release.status === 'DRAFT'
            ? `已作废 ${pendingAction.release.versionName}`
            : `已撤回 ${pendingAction.release.versionName}`,
        )
      }
      setPendingAction(null)
      await load()
    } catch {
      // request layer already toasts the error
    } finally {
      setActing(false)
    }
  }

  async function handleDownload(release: AppRelease) {
    setDownloadingId(release.id)
    try {
      await downloadAppReleaseApk(release)
    } catch {
      // request layer already toasts the error
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <>
      <PageHeader
        title="应用发布"
        description="上传各渠道 APK，发布后手机端就能在软件里检查并安装更新。"
        actions={
          canAdd ? (
            <Button onClick={() => setCreateOpen(true)}>
              <PlusIcon data-icon="inline-start" />
              上传安装包
            </Button>
          ) : null
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>版本列表</CardTitle>
          <CardDescription>按渠道和发布状态筛选。同一渠道的内部版本号必须递增，作废后也不能重用。</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form
            className="flex flex-col gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              setPage(1)
              void load()
            }}
          >
            <div className="grid items-end gap-3 lg:grid-cols-[190px_190px_auto]">
              <FieldGroup className="contents">
                <Field>
                  <FieldLabel>渠道</FieldLabel>
                  <Select value={channel} onValueChange={(value) => {
                    setPage(1)
                    setChannel(value as AppReleaseChannel | 'ALL')
                  }}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="ALL">全部</SelectItem>
                        <SelectItem value="prod">正式</SelectItem>
                        <SelectItem value="test">测试</SelectItem>
                        <SelectItem value="dev">开发</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
                <Field>
                  <FieldLabel>状态</FieldLabel>
                  <Select value={status} onValueChange={(value) => {
                    setPage(1)
                    setStatus(value as AppReleaseStatus | 'ALL')
                  }}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="ALL">全部</SelectItem>
                        <SelectItem value="DRAFT">草稿</SelectItem>
                        <SelectItem value="PUBLISHED">已发布</SelectItem>
                        <SelectItem value="REVOKED">已撤回</SelectItem>
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                </Field>
              </FieldGroup>
              <div className="flex flex-wrap items-center gap-2">
                <Button type="submit" className="flex-1 sm:flex-none">
                  <SearchIcon data-icon="inline-start" />
                  查询
                </Button>
                {hasActiveFilters ? (
                  <Button type="button" variant="outline" className="flex-1 sm:flex-none" onClick={handleReset}>
                    重置
                  </Button>
                ) : null}
              </div>
            </div>
          </form>

          {loading ? (
            <div className="motion-section flex flex-col gap-2">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : loadError ? (
            <Empty>
              <EmptyTitle>版本列表加载失败</EmptyTitle>
              <EmptyDescription>检查网络后重试。</EmptyDescription>
              <Button variant="outline" size="sm" onClick={() => void load()}>重新加载</Button>
            </Empty>
          ) : data && data.items.length > 0 ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>版本</TableHead>
                    <TableHead>渠道</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>安装包</TableHead>
                    <TableHead>强制更新</TableHead>
                    <TableHead>发布时间</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((release) => (
                    <TableRow key={release.id}>
                      <TableCell>
                        <div className="flex min-w-52 items-center gap-3">
                          <div className="flex size-9 items-center justify-center rounded-md bg-secondary">
                            <SmartphoneIcon aria-hidden="true" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{release.versionName}</p>
                            <p className="text-xs text-muted-foreground">内部版本 {release.versionCode}</p>
                            {release.releaseNotes ? (
                              <p className="truncate text-xs text-muted-foreground">{release.releaseNotes}</p>
                            ) : null}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>{CHANNEL_LABELS[release.channel]}</TableCell>
                      <TableCell><ReleaseStatusBadge status={release.status} /></TableCell>
                      <TableCell>
                        <div className="min-w-40">
                          <p className="truncate">{release.fileName}</p>
                          <p className="text-xs text-muted-foreground">{formatBytes(release.sizeBytes)}</p>
                          <p className="truncate text-xs text-muted-foreground">SHA-256 {release.sha256.slice(0, 12)}</p>
                        </div>
                      </TableCell>
                      <TableCell>{release.forceUpdate ? '是' : '否'}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatTime(release.publishedAt)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-wrap justify-end gap-2">
                          {canQuery ? (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={downloadingId === release.id}
                              onClick={() => void handleDownload(release)}
                            >
                              {downloadingId === release.id ? <Spinner data-icon="inline-start" /> : <DownloadIcon data-icon="inline-start" />}
                              下载
                            </Button>
                          ) : null}
                          {canEdit && release.status !== 'REVOKED' ? (
                            <Button variant="outline" size="sm" onClick={() => setEditing(release)}>
                              <PenLineIcon data-icon="inline-start" />
                              编辑
                            </Button>
                          ) : null}
                          {canEdit && release.status === 'DRAFT' ? (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => setPendingAction({ type: 'publish', release })}
                            >
                              发布
                            </Button>
                          ) : null}
                          {canEdit && release.status !== 'REVOKED' ? (
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => setPendingAction({ type: 'revoke', release })}
                            >
                              {release.status === 'DRAFT' ? '作废' : '撤回'}
                            </Button>
                          ) : null}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-muted-foreground">
                  共 {data.total} 条，第 {data.page} / {totalPages} 页
                </p>
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}>
                    <ChevronLeftIcon data-icon="inline-start" />上一页
                  </Button>
                  <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((current) => current + 1)}>
                    下一页<ChevronRightIcon data-icon="inline-end" />
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <Empty>
              <EmptyTitle>{hasActiveFilters ? '没有匹配的版本' : '还没有安装包'}</EmptyTitle>
              <EmptyDescription>
                {hasActiveFilters ? '调整筛选条件后重新查询。' : '上传对应渠道的 APK 并发布后，手机端就能检查更新。'}
              </EmptyDescription>
              {hasActiveFilters ? <Button variant="outline" size="sm" onClick={handleReset}>清空筛选</Button> : null}
            </Empty>
          )}
        </CardContent>
      </Card>

      <UploadReleaseDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={load}
      />

      <EditReleaseDialog
        release={editing}
        onOpenChange={(open) => {
          if (!open) setEditing(null)
        }}
        onSaved={load}
      />

      <Dialog open={pendingAction != null} onOpenChange={(open) => {
        if (!open && !acting) setPendingAction(null)
      }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {pendingAction?.type === 'publish'
                ? '发布这个版本？'
                : pendingAction?.release.status === 'DRAFT'
                  ? '作废这份草稿？'
                  : '撤回这个版本？'}
            </DialogTitle>
            <DialogDescription>
              {pendingAction?.type === 'publish'
                ? `发布后，${pendingAction ? CHANNEL_LABELS[pendingAction.release.channel] : ''}渠道里低于内部版本 ${pendingAction?.release.versionCode ?? ''} 的 App 会看到更新。手机端只会下载该渠道当前最新包。`
                : pendingAction?.release.status === 'DRAFT'
                  ? '作废后不能再发布，这个内部版本号也不能再上传。'
                  : '撤回后，手机端不再提供这个安装包。已经下到手机里的文件不受影响。'}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" disabled={acting} onClick={() => setPendingAction(null)}>取消</Button>
            <Button
              variant={pendingAction?.type === 'revoke' ? 'destructive' : 'default'}
              disabled={acting}
              onClick={() => void handleConfirmAction()}
            >
              {acting ? <Spinner data-icon="inline-start" /> : null}
              {pendingAction?.type === 'publish'
                ? '确认发布'
                : pendingAction?.release.status === 'DRAFT'
                  ? '确认作废'
                  : '确认撤回'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function UploadReleaseDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [channel, setChannel] = useState<AppReleaseChannel>('prod')
  const [versionName, setVersionName] = useState('')
  const [versionCode, setVersionCode] = useState('')
  const [releaseNotes, setReleaseNotes] = useState('')
  const [forceUpdate, setForceUpdate] = useState('false')
  const [file, setFile] = useState<File | null>(null)
  const [saving, setSaving] = useState(false)
  const requestIdRef = useRef(crypto.randomUUID())

  useEffect(() => {
    if (!open) return
    setChannel('prod')
    setVersionName('')
    setVersionCode('')
    setReleaseNotes('')
    setForceUpdate('false')
    setFile(null)
    requestIdRef.current = crypto.randomUUID()
  }, [open])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parsedCode = Number(versionCode)
    if (!versionName.trim()) {
      toast.error('请填写对外版本号')
      return
    }
    if (!Number.isInteger(parsedCode) || parsedCode < 1) {
      toast.error('内部版本号必须是大于 0 的整数')
      return
    }
    if (!file) {
      toast.error('请选择 APK 安装包')
      return
    }
    if (file.size > MAX_APK_BYTES) {
      toast.error('安装包不能超过 150 MB')
      return
    }
    setSaving(true)
    try {
      await createAppRelease({
        channel,
        versionName: versionName.trim(),
        versionCode: parsedCode,
        releaseNotes: releaseNotes.trim() || undefined,
        forceUpdate: forceUpdate === 'true',
        requestId: requestIdRef.current,
        file,
      })
      toast.success(`已上传 ${versionName.trim()}，发布后才会推给手机`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // request layer already toasts the error
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>上传安装包</DialogTitle>
            <DialogDescription>
              先存成草稿，核对渠道和版本号后再发布。开发、测试、正式包不能混用。
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[60vh] overflow-y-auto py-4">
            <FieldGroup>
              <Field>
                <FieldLabel>渠道</FieldLabel>
                <Select value={channel} onValueChange={(value) => setChannel(value as AppReleaseChannel)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="prod">正式</SelectItem>
                      <SelectItem value="test">测试</SelectItem>
                      <SelectItem value="dev">开发</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="release-version-name">对外版本号</FieldLabel>
                <Input
                  id="release-version-name"
                  value={versionName}
                  placeholder="例如 1.0.3"
                  onChange={(event) => setVersionName(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="release-version-code">内部版本号</FieldLabel>
                <Input
                  id="release-version-code"
                  inputMode="numeric"
                  value={versionCode}
                  placeholder="例如 4004"
                  onChange={(event) => setVersionCode(event.target.value)}
                />
                <FieldDescription>必须大于该渠道已上传过的内部版本号，手机端按这个数字判断是否有更新。</FieldDescription>
              </Field>
              <Field>
                <FieldLabel>强制更新</FieldLabel>
                <Select value={forceUpdate} onValueChange={setForceUpdate}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="false">否，允许稍后更新</SelectItem>
                      <SelectItem value="true">是，必须更新后才能继续用</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="release-notes">更新说明</FieldLabel>
                <Textarea
                  id="release-notes"
                  value={releaseNotes}
                  maxLength={MAX_NOTES_LENGTH}
                  placeholder="这次修了什么，方便养兔的人决定要不要马上更新"
                  onChange={(event) => setReleaseNotes(event.target.value)}
                />
                <FieldDescription>{releaseNotes.length} / {MAX_NOTES_LENGTH}</FieldDescription>
              </Field>
              <Field>
                <FieldLabel htmlFor="release-file">APK 文件</FieldLabel>
                <Input
                  id="release-file"
                  type="file"
                  accept=".apk,application/vnd.android.package-archive"
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                />
                <FieldDescription>
                  {file ? `${file.name} · ${formatBytes(file.size)}` : '只接受 .apk，最大 150 MB'}
                </FieldDescription>
              </Field>
            </FieldGroup>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}
              保存草稿
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function EditReleaseDialog({
  release,
  onOpenChange,
  onSaved,
}: {
  release: AppRelease | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [releaseNotes, setReleaseNotes] = useState('')
  const [forceUpdate, setForceUpdate] = useState('false')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!release) return
    setReleaseNotes(release.releaseNotes ?? '')
    setForceUpdate(release.forceUpdate ? 'true' : 'false')
  }, [release])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!release) return
    setSaving(true)
    try {
      await updateAppRelease(release.id, {
        releaseNotes: releaseNotes.trim(),
        forceUpdate: forceUpdate === 'true',
      })
      toast.success(`已更新 ${release.versionName}`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // request layer already toasts the error
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={release != null} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>编辑 {release?.versionName ?? '版本'}</DialogTitle>
            <DialogDescription>
              已发布版本改强制更新后，会立刻影响该渠道还没升上来的手机。
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[60vh] overflow-y-auto py-4">
            <FieldGroup>
              <Field>
                <FieldLabel>强制更新</FieldLabel>
                <Select value={forceUpdate} onValueChange={setForceUpdate}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="false">否，允许稍后更新</SelectItem>
                      <SelectItem value="true">是，必须更新后才能继续用</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="edit-release-notes">更新说明</FieldLabel>
                <Textarea
                  id="edit-release-notes"
                  value={releaseNotes}
                  maxLength={MAX_NOTES_LENGTH}
                  onChange={(event) => setReleaseNotes(event.target.value)}
                />
                <FieldDescription>{releaseNotes.length} / {MAX_NOTES_LENGTH}</FieldDescription>
              </Field>
            </FieldGroup>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={saving} onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}
              保存
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ReleaseStatusBadge({ status }: { status: AppReleaseStatus }) {
  if (status === 'PUBLISHED') {
    return <Badge>已发布</Badge>
  }
  if (status === 'REVOKED') {
    return <Badge variant="secondary">已撤回</Badge>
  }
  return <Badge variant="outline">草稿</Badge>
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}
