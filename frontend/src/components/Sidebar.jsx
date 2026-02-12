import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuth } from '@/context/AuthProvider'
import { Button } from './ui/button'
import { 
    LayoutDashboard, Users as UsersIcon, Building as ShopsIcon, 
    ClipboardList, CalendarClock, Briefcase, Archive, 
    MessageSquare, Mail, Bell, Wrench, FileText, Shield
} from 'lucide-react';
import { getRoleDisplayName } from '@/lib/displayNames';

const allLinks = [
  { href: '/dashboard', label: 'Дашборд', icon: LayoutDashboard, roles: ['RetailAdmin'] },
  { href: '/requests', label: 'Заявки', icon: Briefcase, roles: ['RetailAdmin', 'StoreManager', 'Contractor'] },
  { href: '/requests/archive', label: 'Архив заявок', icon: Archive, roles: ['RetailAdmin', 'StoreManager', 'Contractor'] },
  { href: '/users', label: 'Пользователи', icon: UsersIcon, roles: ['RetailAdmin'] },
  { href: '/shops', label: 'Магазины', icon: ShopsIcon, roles: ['RetailAdmin'] },
  { href: '/shop-contractor-chats', label: 'Чаты', icon: MessageSquare, roles: ['RetailAdmin'] },
  { href: '/messaging', label: 'Рассылки', icon: Mail, roles: ['RetailAdmin'] },
  { href: '/notifications', label: 'Уведомления', icon: Bell, roles: ['RetailAdmin'] },
  { href: '/work-categories', label: 'Виды работ', icon: ClipboardList, roles: ['RetailAdmin'] },
  { href: '/urgency-categories', label: 'Сроки заявок', icon: CalendarClock, roles: ['RetailAdmin'] },
  { href: '/logs', label: 'Логи', icon: FileText, roles: ['RetailAdmin'] },
  { href: '/audit', label: 'Аудит', icon: Shield, roles: ['RetailAdmin'] },
  { href: '/testing', label: 'Тестирование', icon: Wrench, roles: ['RetailAdmin'] },
];

export default function Sidebar({ open, onClose }) {
  const location = useLocation()
  const { user, logout } = useAuth()

  const links = allLinks.filter(link => user && link.roles.includes(user.role));

  return (
    <>

      <div
        className={cn(
          'fixed inset-0 bg-black/30 z-40 md:hidden transition-opacity',
          open ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        )}
        onClick={onClose}
      />

      <aside
        className={cn(

          'fixed top-0 left-0 h-screen w-64 bg-white border-r flex flex-col z-50 transform transition-transform duration-300 md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        

        <div className="h-14 flex items-center px-4 border-b shrink-0">
            <Link to="/" className="text-xl font-bold text-blue-600 flex items-center gap-2" onClick={onClose}>
                MART INN FOOD
            </Link>
        </div>


        <div className="flex-1 overflow-y-auto px-3 py-3 space-y-1 custom-scrollbar">
            {links.map(link => (
                <Link
                    key={link.href}
                    to={link.href}
                    className={cn(
                    'flex items-center gap-3 px-3 py-2 rounded text-sm hover:bg-gray-100 transition-colors',
                    (location.pathname === link.href || (link.href === '/dashboard' && location.pathname === '/')) && 'bg-gray-200 font-semibold'
                    )}
                    onClick={onClose}
                >
                    {link.icon && <link.icon className="h-4 w-4" />}
                    {link.label}
                </Link>
            ))}
        </div>

        <div className="mt-auto border-t p-3 flex flex-col gap-2 shrink-0 bg-white">
          {user ? (
            <div className="flex flex-col items-start gap-1">
              <span className="px-2 text-sm font-medium text-gray-600 truncate w-full" title={user.username}>
                  {user.username}
              </span>
              <span className="px-2 text-xs text-gray-500 mb-1">{getRoleDisplayName(user.role)}</span>
              <Button className="w-full h-8" variant="outline" size="sm" onClick={() => { logout(); onClose() }}>
                Выйти
              </Button>
            </div>
          ) : (
            <Link to="/login" onClick={onClose} className="w-full">
              <Button className="w-full h-8" size="sm">Войти</Button>
            </Link>
          )}
        </div>
      </aside>
    </>
  )
}