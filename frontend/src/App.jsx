import React, { useState } from 'react'
import { Routes, Route } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import Dashboard from './pages/Dashboard'
import Home from './pages/Home'
import Login from './pages/Login'
import Navbar from './components/NavBar'
import Sidebar from './components/Sidebar'
import Users from './pages/Users' // <-- Импортируем новую страницу

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <>
      <Navbar 
        sidebarOpen={sidebarOpen} 
        onMenuClick={() => setSidebarOpen(true)}
        onClose={() => setSidebarOpen(false)}
      />
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="pt-16 md:pl-64">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/users" // <-- Добавляем новый маршрут
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <Users />
              </ProtectedRoute>
            }
          />
        </Routes>
      </div>
    </>
  )
}