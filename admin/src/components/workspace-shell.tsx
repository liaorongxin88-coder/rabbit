import {
  LayoutDashboardIcon,
  LogOutIcon,
  CircleUserRoundIcon,
  RabbitIcon,
  Rows3Icon,
  UsersIcon,
  WarehouseIcon,
} from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { useWorkspace } from '@/lib/workspace'
import { BrandLogo } from '@/components/brand-logo'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/workspace/dashboard', label: '工作概览', icon: LayoutDashboardIcon },
  { to: '/workspace/farms', label: '兔场管理', icon: WarehouseIcon },
  { to: '/workspace/livestock', label: '兔群管理', icon: RabbitIcon },
  { to: '/workspace/production', label: '生产批次', icon: Rows3Icon },
  { to: '/workspace/members', label: '成员权限', icon: UsersIcon },
  { to: '/workspace/account', label: '账号安全', icon: CircleUserRoundIcon },
]

const roleLabels = {
  OWNER: '兔场所有者',
  MANAGER: '兔场管理员',
  STAFF: '生产人员',
  VIEWER: '查看者',
}

export function WorkspaceShell({
  children,
  onLogout,
}: {
  children: React.ReactNode
  onLogout: () => void
}) {
  const workspace = useWorkspace()

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-16 items-center gap-2 px-4">
          <BrandLogo className="h-10 w-12" />
          <Link to="/workspace/dashboard" className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-semibold">Rabbit Farm</span>
            <span className="text-xs text-muted-foreground">兔场工作台</span>
          </Link>
        </div>
        <Separator />
        <FarmSelector compact={false} />
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
              {workspace.permission ? roleLabels[workspace.permission.role] : '未选择兔场'}
            </p>
          </div>
          <Button variant="ghost" size="icon" onClick={onLogout} aria-label="退出兔场工作台">
            <LogOutIcon aria-hidden="true" />
          </Button>
        </div>
      </aside>

      <div className="sticky top-0 z-10 border-b bg-background/95 backdrop-blur lg:hidden">
        <header className="flex h-14 items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <BrandLogo className="h-8 w-9" />
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
            <FarmSelector compact />
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}

function FarmSelector({ compact }: { compact: boolean }) {
  const workspace = useWorkspace()

  return (
    <div className={cn('grid gap-3', compact ? '' : 'p-4')}>
      <label className="flex min-w-0 flex-col gap-1.5 text-xs font-medium text-muted-foreground">
        兔场
        <Select
          value={workspace.selectedHouse?.id.toString() ?? ''}
          onValueChange={(value) => workspace.selectHouse(Number(value))}
          disabled={workspace.loading || workspace.houses.length === 0}
        >
          <SelectTrigger aria-label="选择兔场">
            <WarehouseIcon aria-hidden="true" />
            <SelectValue placeholder="暂无兔场" />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {workspace.houses.map((house) => (
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
