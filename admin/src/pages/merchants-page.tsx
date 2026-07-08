import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Building2Icon, ChevronLeftIcon, ChevronRightIcon, PlusIcon, SearchIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { MerchantFormDialog } from '@/components/merchant-form-dialog'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { listMerchants } from '@/api/merchants'
import type { Merchant, MerchantStatus, PageResult } from '@/types/api'

const PAGE_SIZE = 20

export function MerchantsPage() {
  const [keyword, setKeyword] = useState('')
  const [submittedKeyword, setSubmittedKeyword] = useState('')
  const [status, setStatus] = useState<MerchantStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<PageResult<Merchant> | null>(null)
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)

  const totalPages = useMemo(() => {
    if (!data) {
      return 1
    }
    return Math.max(1, Math.ceil(data.total / data.pageSize))
  }, [data])

  const hasActiveFilters = submittedKeyword.trim() || status !== 'ALL'

  const loadMerchants = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listMerchants({
        page,
        pageSize: PAGE_SIZE,
        keyword: submittedKeyword,
        status,
      })
      setData(result)
    } finally {
      setLoading(false)
    }
  }, [page, status, submittedKeyword])

  useEffect(() => {
    void loadMerchants()
  }, [loadMerchants])

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

  function handleSaved() {
    setPage(1)
    setSubmittedKeyword(keyword)
  }

  return (
    <>
      <PageHeader
        title="商户管理"
        description="管理 SaaS 商户基础资料、启停状态和商户用户绑定。"
        actions={
          <Button onClick={() => setDialogOpen(true)}>
            <PlusIcon data-icon="inline-start" />
            新增商户
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>商户列表</CardTitle>
          <CardDescription>按名称、联系人或电话筛选商户。</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form className="flex flex-col gap-3" onSubmit={handleSearch}>
            <div className="grid items-end gap-3 lg:grid-cols-[minmax(0,1fr)_180px_auto]">
              <FieldGroup className="contents">
                <Field>
                  <FieldLabel htmlFor="keyword">关键词</FieldLabel>
                  <Input
                    id="keyword"
                    value={keyword}
                    placeholder="商户名称 / 联系人 / 电话"
                    onChange={(event) => setKeyword(event.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel>状态</FieldLabel>
                  <Select
                    value={status}
                    onValueChange={(value) =>
                      setStatus(value as MerchantStatus | 'ALL')
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        <SelectItem value="ALL">全部</SelectItem>
                        <SelectItem value="ENABLED">启用</SelectItem>
                        <SelectItem value="DISABLED">停用</SelectItem>
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
                  <Button
                    type="button"
                    variant="outline"
                    className="flex-1 sm:flex-none"
                    onClick={handleReset}
                  >
                    重置
                  </Button>
                ) : null}
              </div>
            </div>
            <p className="text-sm text-muted-foreground">
              按回车或点击查询后更新结果。
            </p>
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
                    <TableHead>商户</TableHead>
                    <TableHead>联系人</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>创建时间</TableHead>
                    <TableHead className="text-right">操作</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.items.map((merchant) => (
                    <TableRow key={merchant.id}>
                      <TableCell>
                        <div className="flex min-w-56 items-center gap-3">
                          <div className="flex size-9 items-center justify-center rounded-md bg-secondary">
                            <Building2Icon aria-hidden="true" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{merchant.name}</p>
                            <p className="truncate text-xs text-muted-foreground">
                              ID {merchant.id}
                            </p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="min-w-40">
                          <p className="truncate">{merchant.contactName || '-'}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            {merchant.contactPhone || '-'}
                          </p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={merchant.status} />
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatTime(merchant.createTime)}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="outline" size="sm" asChild>
                          <Link to={`/merchants/${merchant.id}`}>详情</Link>
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
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page <= 1}
                    onClick={() => setPage((current) => Math.max(1, current - 1))}
                  >
                    <ChevronLeftIcon data-icon="inline-start" />
                    上一页
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page >= totalPages}
                    onClick={() => setPage((current) => current + 1)}
                  >
                    下一页
                    <ChevronRightIcon data-icon="inline-end" />
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <Empty>
              <EmptyTitle>{hasActiveFilters ? '没有匹配的商户' : '暂无商户'}</EmptyTitle>
              <EmptyDescription>
                {hasActiveFilters
                  ? '调整关键词或状态后重新查询。'
                  : '创建第一个商户后，可以绑定业务用户并查看运营数据。'}
              </EmptyDescription>
              {hasActiveFilters ? (
                <Button variant="outline" size="sm" onClick={handleReset}>
                  清空筛选
                </Button>
              ) : null}
            </Empty>
          )}
        </CardContent>
      </Card>

      <MerchantFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onSaved={handleSaved}
      />
    </>
  )
}

function formatTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString()
}
