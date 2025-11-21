import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuth } from '@/context/AuthProvider'
import { Button } from './ui/button'
import { Home as HomeIcon, LayoutDashboard, Users as UsersIcon, Building as ShopsIcon, ClipboardList, CalendarClock, Briefcase, Archive, MessageSquare, Mail, Bell } from 'lucide-react';
import { getRoleDisplayName } from '@/lib/displayNames';

const allLinks = [
  { href: '/dashboard', label: 'Дашборд', icon: LayoutDashboard, roles: ['RetailAdmin', 'StoreManager', 'Contractor'] },
  { href: '/requests', label: 'Заявки', icon: Briefcase, roles: ['RetailAdmin', 'StoreManager', 'Contractor'] },
  { href: '/requests/archive', label: 'Архив заявок', icon: Archive, roles: ['RetailAdmin', 'StoreManager', 'Contractor'] },
  { href: '/users', label: 'Пользователи', icon: UsersIcon, roles: ['RetailAdmin'] },
  { href: '/shops', label: 'Магазины', icon: ShopsIcon, roles: ['RetailAdmin'] },
  { href: '/shop-contractor-chats', label: 'Чаты', icon: MessageSquare, roles: ['RetailAdmin'] },
  { href: '/messaging', label: 'Рассылки', icon: Mail, roles: ['RetailAdmin'] },
  { href: '/notifications', label: 'Уведомления', icon: Bell, roles: ['RetailAdmin'] },
  { href: '/work-categories', label: 'Виды работ', icon: ClipboardList, roles: ['RetailAdmin'] },
  { href: '/urgency-categories', label: 'Сроки заявок', icon: CalendarClock, roles: ['RetailAdmin'] },
];


export default function Sidebar({ open, onClose }) {
  const location = useLocation()
  const { user, logout } = useAuth()

  const links = allLinks.filter(link => user && link.roles.includes(user.role));

  return (
    <>
      <div
        className={cn(
          'fixed inset-0 bg-black/30 z-10 md:hidden transition-opacity',
          open ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        )}
        onClick={onClose}
      />

      <aside
        className={cn(
          'fixed top-16 left-0 h-[calc(100vh-4rem)] w-64 bg-white border-r p-4 flex flex-col gap-2 z-20 transform transition-transform duration-300 md:flex md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        
        {links.map(link => (
            <Link
                key={link.href}
                to={link.href}
                className={cn(
                'flex items-center gap-3 px-3 py-2 rounded hover:bg-gray-100 transition-colors',
                (location.pathname === link.href || (link.href === '/dashboard' && location.pathname === '/')) && 'bg-gray-200 font-semibold'
                )}
                onClick={onClose}
            >
                {link.icon && <link.icon className="h-4 w-4" />}
                {link.label}
            </Link>
        ))}


        <div className="mt-auto border-t pt-4 flex flex-col gap-2">
          {user ? (
            <div className="flex flex-col items-start gap-2">
              <span className="px-3 text-sm font-medium text-gray-600">Пользователь: {user.username}</span>
              <span className="px-3 text-xs text-gray-500">Роль: {getRoleDisplayName(user.role)}</span>
              <Button className="w-full" variant="outline" onClick={() => { logout(); onClose() }}>
                Выйти
              </Button>
            </div>
          ) : (
            <Link to="/login" onClick={onClose} className="w-full">
              <Button className="w-full">Войти</Button>
            </Link>
          )}
        </div>
      </aside>
    </>
  )
}