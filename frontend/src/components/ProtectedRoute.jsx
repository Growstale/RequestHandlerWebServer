import React from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthProvider'

export default function ProtectedRoute({ children, allowedRoles }) {
  const { accessToken, user, loading } = useAuth()
  
  if (loading) return <div className="p-8">Загрузка...</div>
  
  if (!accessToken) return <Navigate to="/login" replace />

  if (allowedRoles && !allowedRoles.includes(user?.role)) {
    // Можно перенаправить на страницу "Нет доступа" или просто на главную
    return <Navigate to="/dashboard" replace />
  }

  return children
}