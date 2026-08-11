import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ChevronLeftIcon, ChevronRightIcon, PlusIcon, SearchIcon, WarehouseIcon } from 'lucide-react'
import { createFarm, listFarms } from '@/api/farms'
import { listBusinessUsers } from '@/api/users'
import { FarmStatusBadge } from '@/components/farm-status-badge'
import { PageHeader } from '@/components/page-header'
import { getOrCreateFarmRequest } from '@/lib/farm-request'
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
import type { AdminFarm, BusinessUser, CreateAdminFarmRequest, FarmStatus, PageResult } from '@/types/api'

const PAGE_SIZE = 20

export function FarmsPage() {
  const [keyword, setKeyword] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [status, setStatus] = useState<FarmStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AdminFarm> | null>(null)
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? PAGE_SIZE))),
    [data],
  )
  const hasActiveFilters = Boolean(submittedKeyword.trim()) || status !== 'ALL'

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await listFarms({
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
      <PageHeader
        title="兔场管理"
        description="查看兔场状态、所有者、成员规模和业务数据摘要。"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <PlusIcon data-icon="inline-start" />新增兔场
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>兔场列表</CardTitle>
          <CardDescription>按兔场名称、ID、所有者或状态筛选。</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form className="flex flex-col gap-3" onSubmit={handleSearch}>
            <div className="grid items-end gap-3 lg:grid-cols-[minmax(0,1fr)_190px_auto]">
              <FieldGroup className="contents">
                <Field>
                  <FieldLabel htmlFor="farm-keyword">关键词</FieldLabel>
                  <Input
                    id="farm-keyword"
                    value={keyword}
                    placeholder="兔场名称 / ID / 所有者"
                    onChange={(event) => setKeyword(event.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel>状态</FieldLabel>
                  <Select value={status} onValueChange={(value) => setStatus(value as FarmStatus | 'ALL')}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="ALL">全部</SelectItem>
                        <SelectItem value="ENABLED">启用</SelectItem>
                        <SelectItem value="SUSPENDED">已暂停</SelectItem>
                        <SelectItem value="ORPHANED">缺少所有者</SelectItem>
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
          ) : data && data.items.length > 0 ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>兔场</TableHead>
                    <TableHead>所有者</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>成员</TableHead>
                    <TableHead>笼位 / 兔只</TableHead>
                    <TableHead>创建时间</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((farm) => (
                    <TableRow key={farm.id}>
                      <TableCell>
                        <div className="flex min-w-52 items-center gap-3">
                          <div className="flex size-9 items-center justify-center rounded-md bg-secondary">
                            <WarehouseIcon aria-hidden="true" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{farm.name}</p>
                            <p className="text-xs text-muted-foreground">ID {farm.id}</p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <span className="block max-w-64 truncate">
                          {farm.ownerNames?.length ? farm.ownerNames.join('、') : '-'}
                        </span>
                      </TableCell>
                      <TableCell><FarmStatusBadge status={farm.status} /></TableCell>
                      <TableCell>{farm.memberCount ?? '-'}</TableCell>
                      <TableCell>{farm.cageCount ?? '-'} / {farm.rabbitCount ?? '-'}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">
                        {formatTime(farm.createTime)}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="outline" size="sm" asChild>
                          <Link to={`/farms/${farm.id}`}>详情</Link>
                        </Button>
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
              <EmptyTitle>{hasActiveFilters ? '没有匹配的兔场' : '暂无兔场'}</EmptyTitle>
              <EmptyDescription>{hasActiveFilters ? '调整筛选条件后重新查询。' : '新增兔场后会显示在这里。'}</EmptyDescription>
              {hasActiveFilters ? <Button variant="outline" size="sm" onClick={handleReset}>清空筛选</Button> : null}
            </Empty>
          )}
        </CardContent>
      </Card>

      <CreateFarmDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSaved={load}
      />
    </>
  )
}

function CreateFarmDialog({
  open,
  onOpenChange,
  onSaved,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSaved: () => Promise<void>
}) {
  const [name, setName] = useState('')
  const [rows, setRows] = useState('1')
  const [cols, setCols] = useState('1')
  const [layers, setLayers] = useState('1')
  const [remark, setRemark] = useState('')
  const [ownerMode, setOwnerMode] = useState<'USER' | 'PHONE'>('USER')
  const [ownerPhone, setOwnerPhone] = useState('')
  const [userKeyword, setUserKeyword] = useState('')
  const [users, setUsers] = useState<BusinessUser[]>([])
  const [selectedUserId, setSelectedUserId] = useState('')
  const [userSearchLoading, setUserSearchLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const pendingRequest = useRef<CreateAdminFarmRequest | null>(null)

  useEffect(() => {
    if (!open) return
    setName('')
    setRows('1')
    setCols('1')
    setLayers('1')
    setRemark('')
    setOwnerMode('USER')
    setOwnerPhone('')
    setUserKeyword('')
    setSelectedUserId('')
    pendingRequest.current = null
    void searchUsers('')
  }, [open])

  async function searchUsers(keyword = userKeyword) {
    setUserSearchLoading(true)
    try {
      const result = await listBusinessUsers({
        pageNum: 1,
        pageSize: 20,
        keyword: keyword.trim(),
        status: 'ENABLED',
      })
      setUsers(result.items)
      setSelectedUserId((current) => (
        result.items.some((user) => String(user.userId) === current) ? current : ''
      ))
    } catch {
      setUsers([])
      setSelectedUserId('')
    } finally {
      setUserSearchLoading(false)
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const layoutRows = Number(rows)
    const layoutCols = Number(cols)
    const layoutLayers = Number(layers)
    if (
      ![layoutRows, layoutCols, layoutLayers].every(
        (value) => Number.isInteger(value) && value > 0 && value <= 100,
      )
    ) {
      toast.error('排数、列数和层数必须为 1-100 的整数')
      return
    }
    if (layoutRows * layoutCols * layoutLayers > 2_000) {
      toast.error('初始笼位数量不能超过 2000')
      return
    }

    const ownerUserId = Number(selectedUserId)
    if (ownerMode === 'USER' && (!Number.isInteger(ownerUserId) || ownerUserId <= 0)) {
      toast.error('请选择初始所有者')
      return
    }
    if (ownerMode === 'PHONE' && !ownerPhone.trim()) {
      toast.error('请输入初始所有者手机号')
      return
    }

    const payload: Omit<CreateAdminFarmRequest, 'requestId'> = {
      name: name.trim(),
      layoutRows,
      layoutCols,
      layoutLayers,
      remark: remark.trim() || undefined,
      ...(ownerMode === 'USER'
        ? { ownerUserId }
        : { ownerPhone: ownerPhone.trim() }),
    }
    const request = getOrCreateFarmRequest(
      pendingRequest.current,
      payload,
      () => crypto.randomUUID(),
    )
    pendingRequest.current = request
    setSaving(true)
    try {
      const farm = await createFarm(request)
      pendingRequest.current = null
      toast.success(`兔场“${farm.name}”已新增`)
      onOpenChange(false)
      await onSaved()
    } catch {
      // Reuse the same requestId when the operator retries an unchanged form.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>新增兔场</DialogTitle>
          <DialogDescription>创建兔场并指定第一位所有者。</DialogDescription>
        </DialogHeader>
        <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
          <FieldGroup className="min-h-0 overflow-y-auto px-1">
            <Field>
              <FieldLabel htmlFor="create-farm-name">兔场名称</FieldLabel>
              <Input id="create-farm-name" maxLength={100} value={name} required onChange={(event) => setName(event.target.value)} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field>
                <FieldLabel htmlFor="create-farm-rows">排数</FieldLabel>
                <Input id="create-farm-rows" type="number" min={1} max={100} value={rows} required onChange={(event) => setRows(event.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="create-farm-cols">列数</FieldLabel>
                <Input id="create-farm-cols" type="number" min={1} max={100} value={cols} required onChange={(event) => setCols(event.target.value)} />
              </Field>
              <Field>
                <FieldLabel htmlFor="create-farm-layers">层数</FieldLabel>
                <Input id="create-farm-layers" type="number" min={1} max={100} value={layers} required onChange={(event) => setLayers(event.target.value)} />
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="create-farm-owner-mode">初始所有者</FieldLabel>
              <Select value={ownerMode} onValueChange={(value) => setOwnerMode(value as 'USER' | 'PHONE')}>
                <SelectTrigger id="create-farm-owner-mode"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="USER">现有业务用户</SelectItem>
                    <SelectItem value="PHONE">手机号邀请身份</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
            </Field>
            {ownerMode === 'USER' ? (
              <Field>
                <FieldLabel htmlFor="create-farm-user-search">查找业务用户</FieldLabel>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Input
                    id="create-farm-user-search"
                    value={userKeyword}
                    placeholder="用户名 / 脱敏手机号 / ID"
                    onChange={(event) => setUserKeyword(event.target.value)}
                  />
                  <Button type="button" variant="outline" disabled={userSearchLoading} onClick={() => void searchUsers()}>
                    {userSearchLoading ? <Spinner data-icon="inline-start" /> : <SearchIcon data-icon="inline-start" />}
                    查询
                  </Button>
                </div>
                <Select value={selectedUserId} onValueChange={setSelectedUserId}>
                  <SelectTrigger aria-label="选择初始所有者"><SelectValue placeholder="选择启用中的业务用户" /></SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {users.map((user) => (
                        <SelectItem key={user.userId} value={String(user.userId)}>
                          {user.userName} · ID {user.userId}{user.phoneMasked ? ` · ${user.phoneMasked}` : ''}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                {users.length === 0 && !userSearchLoading ? (
                  <FieldDescription>没有匹配的启用业务用户。</FieldDescription>
                ) : null}
              </Field>
            ) : (
              <Field>
                <FieldLabel htmlFor="create-farm-owner-phone">所有者手机号</FieldLabel>
                <Input
                  id="create-farm-owner-phone"
                  inputMode="tel"
                  maxLength={32}
                  value={ownerPhone}
                  placeholder="请输入中国大陆手机号"
                  required
                  onChange={(event) => setOwnerPhone(event.target.value)}
                />
                <FieldDescription>手机号验证登录后将进入同一业务身份。</FieldDescription>
              </Field>
            )}
            <Field>
              <FieldLabel htmlFor="create-farm-remark">备注</FieldLabel>
              <Textarea id="create-farm-remark" maxLength={1000} value={remark} onChange={(event) => setRemark(event.target.value)} />
            </Field>
          </FieldGroup>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner data-icon="inline-start" /> : null}创建兔场
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}
