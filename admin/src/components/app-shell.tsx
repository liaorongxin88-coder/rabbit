import {
  LayoutDashboardIcon,
  LogOutIcon,
  MenuIcon,
  UserCogIcon,
  UsersIcon,
  WarehouseIcon,
  XIcon,
} from 'lucide-react'
import { useState } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { BrandLogo } from '@/components/brand-logo'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import type { AdminSession } from '@/types/api'
import { hasPermission } from '@/lib/permissions'
import { cn } from '@/lib/utils'

const navItems = [
  { to: '/dashboard', label: '运营概览', icon: LayoutDashboardIcon },
  { to: '/farms', label: '兔场管理', icon: WarehouseIcon },
  { to: '/users', label: '业务用户', icon: UsersIcon },
  {
    to: '/accounts',
    label: '管理员账号',
    icon: UserCogIcon,
    permission: 'platform:accounts:list',
  },
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
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const visibleNavItems = navItems.filter(
    (item) => !item.permission || hasPermission(session, item.permission),
  )

  return (
    <div className="min-h-screen bg-background">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r bg-card lg:flex lg:flex-col">
        <div className="flex h-16 items-center gap-2 px-4">
          <BrandLogo className="h-10 w-12" />
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
      <div className="sticky top-0 z-20 bg-background/95 backdrop-blur lg:hidden">
        <header className="flex h-14 items-center justify-between border-b px-4">
          <div className="flex min-w-0 items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              aria-label={mobileMenuOpen ? '关闭导航' : '打开导航'}
              aria-controls="platform-mobile-navigation"
              aria-expanded={mobileMenuOpen}
              onClick={() => setMobileMenuOpen((open) => !open)}
            >
              {mobileMenuOpen ? <XIcon aria-hidden="true" /> : <MenuIcon aria-hidden="true" />}
            </Button>
            <Link to="/dashboard" className="flex min-w-0 items-center gap-1.5">
              <BrandLogo className="h-8 w-9" />
              <span className="truncate text-sm font-semibold">Rabbit SaaS</span>
            </Link>
          </div>
          <Button variant="outline" size="sm" onClick={onLogout}>
            <LogOutIcon data-icon="inline-start" />
            退出
          </Button>
        </header>
        {mobileMenuOpen ? (
          <nav
            id="platform-mobile-navigation"
            aria-label="平台主导航"
            className="motion-section flex flex-col gap-1 border-b p-3"
          >
            {visibleNavItems.map((item) => (
              <NavLink
                key={item.label}
                to={item.to}
                onClick={() => setMobileMenuOpen(false)}
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
        ) : null}
      </div>
      <main className="min-h-screen min-w-0 lg:pl-64">
        <div className="motion-page mx-auto flex w-full min-w-0 max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
          {children}
        </div>
      </main>
    </div>
  )
}
