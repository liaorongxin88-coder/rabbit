import {
  Building2Icon,
  LayoutDashboardIcon,
  LogOutIcon,
  RabbitIcon,
  Rows3Icon,
  UsersIcon,
  WarehouseIcon,
} from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { useMerchantWorkspace } from '@/lib/merchant-workspace'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/merchant/dashboard', label: '工作概览', icon: LayoutDashboardIcon },
  { to: '/merchant/houses', label: '兔场管理', icon: WarehouseIcon },
  { to: '/merchant/livestock', label: '兔群管理', icon: RabbitIcon },
  { to: '/merchant/production', label: '生产批次', icon: Rows3Icon },
  { to: '/merchant/members', label: '成员权限', icon: UsersIcon },
]

const merchantRoleLabels = {
  OWNER: '商户所有者',
  ADMIN: '商户管理员',
  MEMBER: '商户成员',
}

export function MerchantShell({
  children,
  onLogout,
}: {
  children: React.ReactNode
  onLogout: () => void
}) {
  const workspace = useMerchantWorkspace()

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-16 items-center gap-3 px-5">
          <div className="flex size-9 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <RabbitIcon aria-hidden="true" />
          </div>
          <Link to="/merchant/dashboard" className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-semibold">Rabbit Farm</span>
            <span className="text-xs text-muted-foreground">商户工作台</span>
          </Link>
        </div>
        <Separator />
        <WorkspaceSelectors compact={false} />
        <Separator />
        <nav className="flex flex-1 flex-col gap-1 p-3">
          {navItems.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'motion-press flex items-center gap-2 rounded-md border border-transparent px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground',
                  isActive && 'border-input bg-secondary text-foreground',
                )
              }
            >
              <item.icon aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <Separator />
        <div className="flex items-center justify-between gap-3 p-4">
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{workspace.session.userName}</p>
            <p className="truncate text-xs text-muted-foreground">
              {workspace.selectedMerchant
                ? merchantRoleLabels[workspace.selectedMerchant.role]
                : '未选择商户'}
            </p>
          </div>
          <Button variant="ghost" size="icon" onClick={onLogout} aria-label="退出商户工作台">
            <LogOutIcon aria-hidden="true" />
          </Button>
        </div>
      </aside>

      <div className="sticky top-0 z-10 border-b bg-background/95 backdrop-blur lg:hidden">
        <header className="flex h-14 items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <RabbitIcon aria-hidden="true" />
            <span className="text-sm font-semibold">Rabbit Farm</span>
          </div>
          <Button variant="outline" size="sm" onClick={onLogout}>
            <LogOutIcon data-icon="inline-start" />
            退出
          </Button>
        </header>
        <nav className="flex w-full gap-1 overflow-x-auto px-3 pb-2">
          {navItems.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex shrink-0 items-center gap-1.5 rounded-md px-3 py-2 text-xs font-medium text-muted-foreground',
                  isActive && 'bg-secondary text-foreground',
                )
              }
            >
              <item.icon aria-hidden="true" />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>

      <main className="min-h-screen lg:pl-64">
        <div className="motion-page mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-5 sm:px-6 lg:px-8">
          <div className="lg:hidden">
            <WorkspaceSelectors compact />
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}

function WorkspaceSelectors({ compact }: { compact: boolean }) {
  const workspace = useMerchantWorkspace()
  const activeMemberships = workspace.memberships.filter(
    (item) => item.membershipStatus === 'ENABLED' && item.merchantStatus === 'ENABLED',
  )

  return (
    <div className={cn('grid gap-3', compact ? 'sm:grid-cols-2' : 'p-4')}>
      <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-muted-foreground">
        商户
        <Select
          value={workspace.selectedMerchant?.merchantId.toString() ?? ''}
          onValueChange={(value) => workspace.selectMerchant(Number(value))}
          disabled={workspace.loading || activeMemberships.length === 0}
        >
          <SelectTrigger aria-label="选择商户">
            <Building2Icon aria-hidden="true" />
            <SelectValue placeholder="请选择商户" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {activeMemberships.map((membership) => (
                <SelectItem key={membership.merchantId} value={membership.merchantId.toString()}>
                  {membership.merchantName}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </label>
      <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-muted-foreground">
        兔场
        <Select
          value={workspace.selectedHouse?.id.toString() ?? ''}
          onValueChange={(value) => workspace.selectHouse(Number(value))}
          disabled={workspace.loading || workspace.merchantHouses.length === 0}
        >
          <SelectTrigger aria-label="选择兔场">
            <WarehouseIcon aria-hidden="true" />
            <SelectValue placeholder="暂无兔场" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {workspace.merchantHouses.map((house) => (
                <SelectItem key={house.id} value={house.id.toString()}>
                  {house.name}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </label>
    </div>
  )
}
