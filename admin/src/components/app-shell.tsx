import {
  Building2Icon,
  LayoutDashboardIcon,
  LogOutIcon,
  MenuIcon,
  UserCogIcon,
} from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import type { AdminSession } from '@/types/api'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/dashboard', label: '运营概览', icon: LayoutDashboardIcon },
  { to: '/merchants', label: '商户管理', icon: Building2Icon },
  { to: '/accounts', label: '账号管理', icon: UserCogIcon, superOnly: true },
]

export function AppShell({
  children,
  session,
  onLogout,
}: {
  children: React.ReactNode
  session: AdminSession
  onLogout: () => void
}) {
  const visibleNavItems = navItems.filter(
    (item) => !item.superOnly || session.role === 'SUPER_ADMIN',
  )

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-16 items-center gap-3 px-5">
          <div className="flex size-9 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <LayoutDashboardIcon aria-hidden="true" />
          </div>
          <Link to="/dashboard" className="flex flex-col">
            <span className="text-sm font-semibold">Rabbit SaaS</span>
            <span className="text-xs text-muted-foreground">平台管理端</span>
          </Link>
        </div>
        <Separator />
        <nav className="flex flex-1 flex-col gap-1 p-3">
          {visibleNavItems.map((item) => (
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
            <p className="truncate text-sm font-medium">{session.userName}</p>
            <p className="text-xs text-muted-foreground">{session.role}</p>
          </div>
          <Button variant="ghost" size="icon" onClick={onLogout} aria-label="退出登录">
            <LogOutIcon aria-hidden="true" />
          </Button>
        </div>
      </aside>
      <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b bg-background/95 px-4 backdrop-blur lg:hidden">
        <div className="flex items-center gap-2">
          <MenuIcon aria-hidden="true" />
          <span className="text-sm font-semibold">Rabbit SaaS</span>
        </div>
        <Button variant="outline" size="sm" onClick={onLogout}>
          <LogOutIcon data-icon="inline-start" />
          退出
        </Button>
      </header>
      <main className="min-h-screen lg:pl-64">
        <div className="motion-page mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </div>
      </main>
    </div>
  )
}
