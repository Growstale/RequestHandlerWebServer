import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/context/AuthProvider'
import { Menu, X } from 'lucide-react'

export default function Navbar() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)

  return (
    <header className="bg-white shadow-md fixed top-0 left-0 w-full z-20">
      <div className="container mx-auto px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-2xl font-bold text-blue-600">MART INN FOOD</Link>
        </div>

        <nav className="hidden md:flex items-center gap-4">
          <Link to="/" className="hover:underline">Главная</Link>
          {user ? (
            <>
              <span className="text-gray-600">{user.username}</span>
              <Button variant="default" onClick={logout}>Выйти</Button>
              <Button asChild variant="default">
                <Link to="/dashboard">Дашборд</Link>
              </Button>
            </>
          ) : (
            <Button asChild variant="default">
              <Link to="/login">Войти</Link>
            </Button>
          )}
        </nav>

        <div className="md:hidden">
          <button
            aria-label={open ? "Закрыть меню" : "Открыть меню"}
            onClick={() => setOpen(v => !v)}
            className="p-2 rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2"
          >
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>

      {open && (
        <div className="md:hidden bg-white border-t">
          <div className="px-4 py-3 flex flex-col gap-2">
            <Link to="/" onClick={() => setOpen(false)} className="py-2">Главная</Link>
            {user ? (
              <>
                <div className="py-2 text-gray-600">{user.username}</div>
                <button onClick={() => { logout(); setOpen(false) }} className="py-2 text-left">Выйти</button>
                <Link to="/dashboard" onClick={() => setOpen(false)} className="py-2">Дашборд</Link>
              </>
            ) : (
              <Link to="/login" onClick={() => setOpen(false)} className="py-2">Войти</Link>
            )}
          </div>
        </div>
      )}
    </header>
  )
}