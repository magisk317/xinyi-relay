import {
  Badge,
  Button,
  Footer,
  Navbar,
  NavbarBrand,
  NavbarCollapse,
  NavbarLink,
  NavbarToggle,
  Sidebar,
  SidebarCTA,
  SidebarItem,
  SidebarItemGroup,
  SidebarItems
} from 'flowbite-react'
import { ClipboardListIcon, HomeIcon, StarIcon } from 'flowbite-react/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import { cx } from './template'

const navItems = [
  { to: '/overview', label: '概览', icon: HomeIcon },
  { to: '/analytics', label: '统计', icon: StarIcon },
  { to: '/apps', label: '应用', icon: ClipboardListIcon },
  { to: '/records', label: '记录', icon: ClipboardListIcon },
  { to: '/senders', label: '通道', icon: StarIcon },
  { to: '/settings', label: '设置', icon: HomeIcon },
  { to: '/advanced', label: '高级', icon: ClipboardListIcon }
]

export function AppLayout() {
  const { username, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <div className="min-h-screen bg-gray-50 text-slate-900">
      <Navbar fluid className="fixed left-0 right-0 top-0 z-30 border-b border-slate-200/80 bg-white/90 px-4 py-3 shadow-sm backdrop-blur lg:px-6">
        <div className="mx-auto flex w-full max-w-[1600px] items-center justify-between">
          <NavbarBrand href="/overview">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-[linear-gradient(135deg,#0f172a,#0ea5e9)] text-base font-semibold text-white">
                R
              </div>
              <div>
                <div className="text-base font-semibold text-slate-950">信驿 Relay WebUI</div>
                <div className="text-xs text-slate-500">嵌入式控制台</div>
              </div>
            </div>
          </NavbarBrand>
          <div className="flex items-center gap-2">
            <Badge color="info" className="hidden sm:flex">
              已登录：{username}
            </Badge>
            <Button color="alternative" size="sm" onClick={() => void logout()}>
              退出登录
            </Button>
            <NavbarToggle />
          </div>
          <NavbarCollapse>
            {navItems.map((item) => (
              <NavbarLink
                key={item.to}
                href={item.to}
                active={location.pathname === item.to}
                onClick={(event) => {
                  event.preventDefault()
                  navigate(item.to)
                }}
              >
                {item.label}
              </NavbarLink>
            ))}
          </NavbarCollapse>
        </div>
      </Navbar>

      <div className="flex items-start pt-16">
        <aside className="fixed left-0 top-16 hidden h-[calc(100vh-4rem)] w-72 px-4 py-6 lg:block">
          <Sidebar
            aria-label="Relay WebUI Navigation"
            className="h-full [&_div]:h-full [&_div]:rounded-3xl [&_div]:border [&_div]:border-slate-200/80 [&_div]:bg-white [&_div]:shadow-sm"
          >
            <SidebarItems>
              <SidebarItemGroup>
                {navItems.map((item) => (
                  <SidebarItem
                    key={item.to}
                    active={location.pathname === item.to}
                    icon={item.icon}
                    as="button"
                    className={cx(
                      'w-full rounded-2xl text-left transition',
                      location.pathname === item.to && 'bg-cyan-50'
                    )}
                    onClick={() => navigate(item.to)}
                  >
                    {item.label}
                  </SidebarItem>
                ))}
              </SidebarItemGroup>
              <SidebarCTA color="blue">
                <p className="mb-2 text-sm font-semibold">WebUI 运行状态</p>
                <p className="text-sm leading-6 text-slate-600">请确认状态栏中的 WebUI 前台服务仍在运行。</p>
              </SidebarCTA>
            </SidebarItems>
          </Sidebar>
        </aside>

        <main className="relative min-h-[calc(100vh-4rem)] w-full overflow-y-auto px-4 py-6 lg:ml-72 lg:px-6">
          <div className="mx-auto w-full max-w-[1280px]">
            <div className="mb-4 flex gap-2 overflow-x-auto lg:hidden">
              {navItems.map((item) => (
                <Button
                  key={item.to}
                  color={location.pathname === item.to ? 'info' : 'alternative'}
                  pill
                  size="sm"
                  onClick={() => navigate(item.to)}
                >
                  {item.label}
                </Button>
              ))}
            </div>
            <Outlet />
            <div className="mt-6">
              <Footer container className="rounded-3xl border border-slate-200/80 bg-white shadow-sm">
                <div className="w-full text-sm text-slate-500 sm:flex sm:items-center sm:justify-between">
                  <span>Relay WebUI</span>
                  <span className="mt-2 block sm:mt-0">
                    如果页面长期无响应，请回到主应用前台或检查 WebUI 前台服务通知。
                  </span>
                </div>
              </Footer>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
