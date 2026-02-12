import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '@/context/AuthProvider'
import { Menu, X } from 'lucide-react'

export default function Navbar({ onMenuClick, sidebarOpen, onClose }) {
  const { user, logout } = useAuth()
  
  return (
    <header className="bg-white shadow-md fixed top-0 left-0 w-full z-20 h-14 flex items-center">
      <div className="container mx-auto px-4 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-xl font-bold text-blue-600">
            MART INN FOOD
          </Link>
        </div>

        <div className="md:hidden">
          <button
            aria-label={sidebarOpen ? "Закрыть меню" : "Открыть меню"}
            onClick={sidebarOpen ? onClose : onMenuClick}
            className="p-2 rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2"
          >
            {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </div>
    </header>
  )
}
