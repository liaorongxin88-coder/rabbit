import { useEffect, useState } from 'react'
import { Edit3Icon, PlusIcon, Trash2Icon, WarehouseIcon } from 'lucide-react'
import { toast } from 'sonner'
import {
  createWorkspaceHouse,
  deleteWorkspaceHouse,
  updateWorkspaceHouse,
} from '@/api/workspace'
import { PageHeader } from '@/components/page-header'
import { HousePermissionBadge, MerchantRoleBadge } from '@/components/permission-badge'
import { hasPermission, useMerchantWorkspace } from '@/lib/merchant-workspace'
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
  DialogTrigger,
} from '@/components/ui/dialog'
import { Empty, EmptyDescription, EmptyTitle } from '@/components/ui/empty'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'
import type { RabbitHouse } from '@/types/api'

export function MerchantHousesPage() {
  const workspace = useMerchantWorkspace()
  const [createOpen, setCreateOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const canEdit = hasPermission(workspace.permission, 'rabbit:houses:edit')
  const canDelete = hasPermission(workspace.permission, 'rabbit:houses:remove')

  return (
    <>
      <PageHeader
        title="兔场管理"
        description={workspace.selectedMerchant?.merchantName ?? '请选择商户'}
        actions={
          <>
            {workspace.selectedMerchant ? <MerchantRoleBadge role={workspace.selectedMerchant.role} /> : null}
            <HouseDialog
              mode="create"
              open={createOpen}
              onOpenChange={setCreateOpen}
              disabled={!workspace.canCreateHouse || !workspace.selectedMerchant}
              onSaved={workspace.refresh}
            />
          </>
        }
      />

      {workspace.merchantHouses.length === 0 ? (
        <Empty>
          <WarehouseIcon aria-hidden="true" />
          <EmptyTitle>当前商户还没有兔场</EmptyTitle>
          <EmptyDescription>
            {workspace.canCreateHouse ? '创建兔场后会自动生成初始笼位。' : '请联系商户所有者或管理员创建兔场。'}
          </EmptyDescription>
        </Empty>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>兔场列表</CardTitle>
            <CardDescription>切换当前兔场后，其余业务页面会同步更新。</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>兔场</TableHead>
                  <TableHead>布局</TableHead>
                  <TableHead>备注</TableHead>
                  <TableHead>当前权限</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {workspace.merchantHouses.map((house) => {
                  const selected = house.id === workspace.selectedHouse?.id
                  return (
                    <TableRow key={house.id}>
                      <TableCell>
                        <div className="flex min-w-40 flex-col gap-1">
                          <span className="font-medium">{house.name}</span>
                          <span className="text-xs text-muted-foreground">ID {house.id}</span>
                        </div>
                      </TableCell>
                      <TableCell>{house.layoutRows} 排 × {house.layoutCols} 列 × {house.layoutLayers} 层</TableCell>
                      <TableCell className="max-w-64 truncate">{house.remark || '-'}</TableCell>
                      <TableCell>
                        {selected ? <HousePermissionBadge permission={workspace.permission} /> : <Badge variant="secondary">未选中</Badge>}
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          {!selected ? (
                            <Button variant="outline" size="sm" onClick={() => workspace.selectHouse(house.id)}>
                              设为当前
                            </Button>
                          ) : (
                            <>
                              <Button variant="outline" size="sm" onClick={() => setEditOpen(true)} disabled={!canEdit}>
                                <Edit3Icon data-icon="inline-start" />
                                编辑
                              </Button>
                              <Button variant="destructive" size="sm" onClick={() => setDeleteOpen(true)} disabled={!canDelete}>
                                <Trash2Icon data-icon="inline-start" />
                                删除
                              </Button>
                            </>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <HouseDialog
        mode="edit"
        house={workspace.selectedHouse}
        open={editOpen}
        onOpenChange={setEditOpen}
        onSaved={workspace.refresh}
      />
      <DeleteHouseDialog
        house={workspace.selectedHouse}
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onDeleted={workspace.refresh}
      />
    </>
  )
}

function HouseDialog({
  mode,
  house,
  open,
  onOpenChange,
  disabled,
  onSaved,
}: {
  mode: 'create' | 'edit'
  house?: RabbitHouse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  disabled?: boolean
  onSaved: () => Promise<void>
}) {
  const workspace = useMerchantWorkspace()
  const [name, setName] = useState('')
  const [rows, setRows] = useState('1')
  const [cols, setCols] = useState('1')
  const [layers, setLayers] = useState('1')
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    setName(house?.name ?? '')
    setRows(String(house?.layoutRows ?? 1))
    setCols(String(house?.layoutCols ?? 1))
    setLayers(String(house?.layoutLayers ?? 1))
    setRemark(house?.remark ?? '')
  }, [house, open])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!workspace.selectedMerchant) {
      return
    }
    setSaving(true)
    try {
      if (mode === 'create') {
        await createWorkspaceHouse({
          merchantId: workspace.selectedMerchant.merchantId,
          name: name.trim(),
          layoutRows: Number(rows),
          layoutCols: Number(cols),
          layoutLayers: Number(layers),
          remark: remark.trim(),
        })
        toast.success('兔场已创建')
      } else if (house) {
        await updateWorkspaceHouse(house.id, { name: name.trim(), remark: remark.trim() })
        toast.success('兔场资料已更新')
      }
      onOpenChange(false)
      await onSaved()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  const form = (
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{mode === 'create' ? '创建兔场' : '编辑兔场'}</DialogTitle>
        <DialogDescription>
          {mode === 'create' ? '布局用于一次性生成初始笼位。' : '布局生成后不可在这里批量重建。'}
        </DialogDescription>
      </DialogHeader>
      <form className="flex min-h-0 flex-col gap-5" onSubmit={handleSubmit}>
        <FieldGroup className="overflow-y-auto pr-1">
          <Field>
            <FieldLabel htmlFor={`${mode}-house-name`}>兔场名称</FieldLabel>
            <Input id={`${mode}-house-name`} value={name} required maxLength={100} onChange={(event) => setName(event.target.value)} />
          </Field>
          {mode === 'create' ? (
            <div className="grid gap-4 sm:grid-cols-3">
              <NumberField id="house-rows" label="排数" value={rows} onChange={setRows} />
              <NumberField id="house-cols" label="列数" value={cols} onChange={setCols} />
              <NumberField id="house-layers" label="层数" value={layers} onChange={setLayers} />
            </div>
          ) : null}
          <Field>
            <FieldLabel htmlFor={`${mode}-house-remark`}>备注</FieldLabel>
            <Textarea id={`${mode}-house-remark`} value={remark} onChange={(event) => setRemark(event.target.value)} />
          </Field>
        </FieldGroup>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button type="submit" disabled={saving}>
            {saving ? <Spinner data-icon="inline-start" /> : null}
            {mode === 'create' ? '创建' : '保存'}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  )

  if (mode === 'create') {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogTrigger asChild>
          <Button disabled={disabled}>
            <PlusIcon data-icon="inline-start" />
            创建兔场
          </Button>
        </DialogTrigger>
        {form}
      </Dialog>
    )
  }
  return <Dialog open={open} onOpenChange={onOpenChange}>{form}</Dialog>
}

function NumberField({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (value: string) => void }) {
  return (
    <Field>
      <FieldLabel htmlFor={id}>{label}</FieldLabel>
      <Input id={id} type="number" min={1} max={100} value={value} required onChange={(event) => onChange(event.target.value)} />
    </Field>
  )
}

function DeleteHouseDialog({
  house,
  open,
  onOpenChange,
  onDeleted,
}: {
  house: RabbitHouse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onDeleted: () => Promise<void>
}) {
  const [saving, setSaving] = useState(false)

  async function handleDelete() {
    if (!house) {
      return
    }
    setSaving(true)
    try {
      await deleteWorkspaceHouse(house.id)
      toast.success('兔场已删除')
      onOpenChange(false)
      await onDeleted()
    } catch {
      // Shared request feedback is sufficient.
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>确认删除兔场</DialogTitle>
          <DialogDescription>将删除“{house?.name ?? ''}”及其访问入口；存在业务数据时后端会拒绝不安全删除。</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button variant="destructive" onClick={() => void handleDelete()} disabled={saving}>
            {saving ? <Spinner data-icon="inline-start" /> : <Trash2Icon data-icon="inline-start" />}
            删除兔场
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
