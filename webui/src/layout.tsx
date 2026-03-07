import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from './auth'

const navItems = [
  { to: '/overview', label: '概览' },
  { to: '/apps', label: '应用' },
  { to: '/records', label: '记录' },
  { to: '/senders', label: '通道' },
  { to: '/settings', label: '设置' },
  { to: '/advanced', label: '高级' }
]

export function AppLayout() {
  const { username, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <header className="border-b bg-white">
        <div className="mx-auto flex w-full max-w-7xl items-center justify-between px-4 py-3">
          <div>
            <h1 className="text-lg font-semibold">信驿 Relay WebUI</h1>
            <p className="text-xs text-slate-500">已登录：{username}</p>
          </div>
          <button
            className="rounded bg-slate-900 px-3 py-1.5 text-sm text-white"
            onClick={() => void logout()}
          >
            退出登录
          </button>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-7xl gap-4 px-4 py-4 max-md:flex-col">
        <aside className="w-52 shrink-0 rounded-lg border bg-white p-2 max-md:w-full">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `mb-1 block rounded px-3 py-2 text-sm ${
                  isActive ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </aside>

        <main className="min-w-0 flex-1 rounded-lg border bg-white p-4">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
