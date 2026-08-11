import { useCallback, useEffect, useMemo, useState } from 'react'
import { ChevronLeftIcon, ChevronRightIcon, PencilIcon, PlusIcon, SearchIcon, Trash2Icon, UserCogIcon } from 'lucide-react'
import { toast } from 'sonner'
import { deleteAdminAccount, listAdminAccounts } from '@/api/accounts'
import { AdminAccountFormDialog } from '@/components/admin-account-form-dialog'
import { PageHeader } from '@/components/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { AdminAccount, AdminRole, PageResult } from '@/types/api'

const PAGE_SIZE = 20

export function AccountsPage({ currentAdminId }: { currentAdminId: number }) {
  const [keyword, setKeyword] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<AdminAccount> | null>(null)
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState<AdminAccount | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? PAGE_SIZE))),
    [data],
  )
  const hasActiveFilters = Boolean(submittedKeyword.trim())

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await listAdminAccounts({ page, pageSize: PAGE_SIZE, keyword: submittedKeyword }))
    } catch {
      setData(null)
    } finally {
      setLoading(false)
    }
  }, [page, submittedKeyword])

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
    setPage(1)
  }

  function openCreateDialog() {
    setEditingAccount(null)
    setDialogOpen(true)
  }

  function openEditDialog(account: AdminAccount) {
    setEditingAccount(account)
    setDialogOpen(true)
  }

  async function handleDelete(account: AdminAccount) {
    if (account.id === currentAdminId) {
      toast.error('不能删除当前登录账号')
      return
    }
    if (!window.confirm(`确认删除管理员账号“${account.userName}”？`)) return
    setDeletingId(account.id)
    try {
      await deleteAdminAccount(account.id)
      toast.success('管理员账号已删除')
      await load()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <>
      <PageHeader
        title="管理员账号"
        description="管理平台控制台登录账号；业务用户在独立页面维护。"
        actions={<Button onClick={openCreateDialog}><PlusIcon data-icon="inline-start" />新增管理员</Button>}
      />
      <Card>
        <CardHeader><CardTitle>管理员列表</CardTitle><CardDescription>按用户名筛选平台管理员账号。</CardDescription></CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form className="flex flex-col gap-3" onSubmit={handleSearch}>
            <div className="grid items-end gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
              <FieldGroup className="contents"><Field><FieldLabel htmlFor="admin-account-keyword">关键词</FieldLabel><Input id="admin-account-keyword" value={keyword} placeholder="用户名" onChange={(event) => setKeyword(event.target.value)} /></Field></FieldGroup>
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
                <TableHeader><TableRow><TableHead>账号</TableHead><TableHead>角色</TableHead><TableHead>状态</TableHead><TableHead>最近登录</TableHead><TableHead>创建时间</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader>
                <TableBody>
                  {data.items.map((account) => (
                    <TableRow key={account.id}>
                      <TableCell>
                        <div className="flex min-w-52 items-center gap-3"><div className="flex size-9 items-center justify-center rounded-md bg-secondary"><UserCogIcon aria-hidden="true" /></div><div className="min-w-0"><p className="truncate font-medium">{account.userName}</p><p className="text-xs text-muted-foreground">ID {account.id}</p></div></div>
                      </TableCell>
                      <TableCell>{roleLabel(account.role)}</TableCell>
                      <TableCell><Badge variant={account.enabled ? 'secondary' : 'outline'}>{account.enabled ? '启用' : '停用'}</Badge></TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{formatTime(account.lastLoginTime)}</TableCell>
                      <TableCell className="whitespace-nowrap text-muted-foreground">{formatTime(account.createTime)}</TableCell>
                      <TableCell className="text-right">
                        <div className="inline-flex items-center gap-2">
                          <Button variant="outline" size="sm" onClick={() => openEditDialog(account)}><PencilIcon data-icon="inline-start" />编辑</Button>
                          <Button variant="destructive" size="sm" disabled={account.id === currentAdminId || deletingId === account.id} onClick={() => void handleDelete(account)}><Trash2Icon data-icon="inline-start" />删除</Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
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
            <Empty><EmptyTitle>{hasActiveFilters ? '没有匹配的管理员' : '暂无管理员账号'}</EmptyTitle><EmptyDescription>{hasActiveFilters ? '调整关键词后重新查询。' : '创建账号后，管理员可以登录平台控制台。'}</EmptyDescription>{hasActiveFilters ? <Button variant="outline" size="sm" onClick={handleReset}>清空筛选</Button> : <Button size="sm" onClick={openCreateDialog}><PlusIcon data-icon="inline-start" />新增管理员</Button>}</Empty>
          )}
        </CardContent>
      </Card>

      <AdminAccountFormDialog open={dialogOpen} account={editingAccount} onOpenChange={setDialogOpen} onSaved={() => { setPage(1); void load() }} />
    </>
  )
}

function roleLabel(role: AdminRole) {
  return role === 'SUPER_ADMIN' ? '超级管理员' : '管理员'
}

function formatTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('zh-CN')
}
