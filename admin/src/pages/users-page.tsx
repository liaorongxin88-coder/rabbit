import { useCallback, useEffect, useMemo, useState } from 'react'
import { ChevronLeftIcon, ChevronRightIcon, SearchIcon, ShieldAlertIcon, UserRoundIcon } from 'lucide-react'
import { toast } from 'sonner'
import { listBusinessUsers, updateBusinessUserStatus } from '@/api/users'
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
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
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
import type { BusinessUser, PageResult, UserStatus } from '@/types/api'

const PAGE_SIZE = 20

export function UsersPage() {
  const [keyword, setKeyword] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [status, setStatus] = useState<UserStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<BusinessUser> | null>(null)
  const [loading, setLoading] = useState(true)
  const [statusTarget, setStatusTarget] = useState<BusinessUser | null>(null)

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? PAGE_SIZE))),
    [data],
  )
  const hasActiveFilters = Boolean(submittedKeyword.trim()) || status !== 'ALL'

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await listBusinessUsers({
        pageNum: page,
        pageSize: PAGE_SIZE,
        keyword: submittedKeyword,
        status,
      }))
    } catch {
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [page, status, submittedKeyword])

  useEffect(() => {
    void load()
  }, [load])

  function handleSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPage(1)
    setSubmittedKeyword(keyword)
  }

  function handleReset() {
    setKeyword('')
    setSubmittedKeyword('')
    setStatus('ALL')
    setPage(1)
  }

  return (
    <>
      <PageHeader title="业务用户" description="查看手机号绑定、兔场关联和账号可用状态。" />
      <Card>
        <CardHeader>
          <CardTitle>用户列表</CardTitle>
          <CardDescription>按用户名、用户 ID 或脱敏手机号筛选。</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form className="flex flex-col gap-3" onSubmit={handleSearch}>
            <div className="grid items-end gap-3 lg:grid-cols-[minmax(0,1fr)_180px_auto]">
              <FieldGroup className="contents">
                <Field>
                  <FieldLabel htmlFor="business-user-keyword">关键词</FieldLabel>
                  <Input id="business-user-keyword" value={keyword} placeholder="用户名 / 用户 ID / 手机号" onChange={(event) => setKeyword(event.target.value)} />
                </Field>
                <Field>
                  <FieldLabel>状态</FieldLabel>
                  <Select value={status} onValueChange={(value) => setStatus(value as UserStatus | 'ALL')}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent><SelectGroup><SelectItem value="ALL">全部</SelectItem><SelectItem value="ENABLED">启用</SelectItem><SelectItem value="DISABLED">停用</SelectItem></SelectGroup></SelectContent>
                  </Select>
                </Field>
              </FieldGroup>
              <div className="flex flex-wrap items-center gap-2">
                <Button type="submit" className="flex-1 sm:flex-none"><SearchIcon data-icon="inline-start" />查询</Button>
                {hasActiveFilters ? <Button type="button" variant="outline" className="flex-1 sm:flex-none" onClick={handleReset}>重置</Button> : null}
              </div>
            </div>
          </form>

          {loading ? (
            <div className="motion-section flex flex-col gap-2"><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /><Skeleton className="h-12 w-full" /></div>
          ) : data && data.items.length > 0 ? (
            <>
              <Table>
                <TableHeader><TableRow><TableHead>用户</TableHead><TableHead>手机号</TableHead><TableHead>兔场数量</TableHead><TableHead>状态</TableHead><TableHead>最近登录</TableHead><TableHead>创建时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
                <TableBody>
                  {data.items.map((user) => {
                    const enabled = user.status ? user.status === 'ENABLED' : user.enabled
                    return (
                      <TableRow key={user.userId}>
                        <TableCell>
                          <div className="flex min-w-52 items-center gap-3">
                            <div className="flex size-9 items-center justify-center rounded-md bg-secondary"><UserRoundIcon aria-hidden="true" /></div>
                            <div className="min-w-0"><p className="truncate font-medium">{user.userName}</p><p className="text-xs text-muted-foreground">ID {user.userId}</p></div>
                          </div>
                        </TableCell>
                        <TableCell>{user.phoneBound ? user.phoneMasked || '已绑定' : '未绑定'}</TableCell>
                        <TableCell>{user.houseCount}</TableCell>
                        <TableCell><Badge variant={enabled ? 'secondary' : 'outline'}>{enabled ? '启用' : '停用'}</Badge></TableCell>
                        <TableCell className="whitespace-nowrap text-muted-foreground">{formatTime(user.lastLoginTime)}</TableCell>
                        <TableCell className="whitespace-nowrap text-muted-foreground">{formatTime(user.createTime)}</TableCell>
                        <TableCell className="text-right">
                          <Button variant={enabled ? 'destructive' : 'default'} size="sm" onClick={() => setStatusTarget(user)}>
                            <ShieldAlertIcon data-icon="inline-start" />{enabled ? '停用' : '启用'}
                          </Button>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
              <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
                <p className="text-sm text-muted-foreground">共 {data.total} 条，第 {data.page} / {totalPages} 页</p>
                <div className="flex items-center gap-2">
                  <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))}><ChevronLeftIcon data-icon="inline-start" />上一页</Button>
                  <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage((current) => current + 1)}>下一页<ChevronRightIcon data-icon="inline-end" /></Button>
                </div>
              </div>
            </>
          ) : (
            <Empty><EmptyTitle>{hasActiveFilters ? '没有匹配的业务用户' : '暂无业务用户'}</EmptyTitle><EmptyDescription>{hasActiveFilters ? '调整筛选条件后重新查询。' : '用户完成注册后会显示在这里。'}</EmptyDescription>{hasActiveFilters ? <Button variant="outline" size="sm" onClick={handleReset}>清空筛选</Button> : null}</Empty>
          )}
        </CardContent>
      </Card>

      <UserStatusDialog
        user={statusTarget}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        onSaved={load}
      />
    </>
  )
}

function UserStatusDialog({
  user,
  onOpenChange,
  onSaved,
}: {
  user: BusinessUser | null
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)
  const enabled = user?.status ? user.status === 'ENABLED' : user?.enabled ?? false
  const nextStatus: UserStatus = enabled ? 'DISABLED' : 'ENABLED'

  async function handleSave() {
    if (!user) return
    setSaving(true)
    try {
      await updateBusinessUserStatus(user.userId, nextStatus)
      toast.success(nextStatus === 'ENABLED' ? '业务用户已启用' : '业务用户已停用')
      onOpenChange(false)
      await onSaved()
    } catch {
      // Keep the dialog open; the shared request layer shows the server reason.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={Boolean(user)} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{nextStatus === 'ENABLED' ? '启用业务用户' : '停用业务用户'}</DialogTitle>
          <DialogDescription>
            {nextStatus === 'ENABLED'
              ? `启用“${user?.userName ?? ''}”后，该用户可以重新登录并访问有效兔场。`
              : `停用“${user?.userName ?? ''}”后，该用户将无法登录；兔场和成员数据不会删除。`}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant={nextStatus === 'ENABLED' ? 'default' : 'destructive'} disabled={saving} onClick={() => void handleSave()}>
            {saving ? <Spinner data-icon="inline-start" /> : null}确认{nextStatus === 'ENABLED' ? '启用' : '停用'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}
