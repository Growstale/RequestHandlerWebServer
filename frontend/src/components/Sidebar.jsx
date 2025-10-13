import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuth } from '@/context/AuthProvider'
import { Button } from './ui/button'

const links = [
  { href: '/', label: 'Главная' },
  { href: '/dashboard', label: 'Дашборд' },
  { href: '/profile', label: 'Профиль' },
]

export default function Sidebar({ open, onClose }) {
  const location = useLocation()
  const { user, logout } = useAuth()

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
              'px-3 py-2 rounded hover:bg-gray-100 transition-colors',
              location.pathname === link.href && 'bg-gray-200 font-semibold'
            )}
            onClick={onClose} 
          >
            {link.label}
          </Link>
        ))}

        <div className="mt-4 border-t pt-4 flex flex-col gap-2">
          {user ? (
            <>
              <span className="text-gray-600">{user.username}</span>
              <Button variant="default" onClick={() => { logout(); onClose() }}>
                Выйти
              </Button>
            </>
          ) : (
            <Link to="/login" onClick={onClose}>
              <Button variant="default">Войти</Button>
            </Link>
          )}
        </div>
      </aside>
    </>
  )
}
