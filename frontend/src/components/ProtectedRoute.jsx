// src/components/ProtectedRoute.jsx
import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthProvider'

export default function ProtectedRoute({ children, allowedRoles }) {
  const { accessToken, user, loading } = useAuth()
  const location = useLocation()
  
  if (loading) return <div className="p-8">Загрузка...</div>
  
  if (!accessToken) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (allowedRoles && !allowedRoles.includes(user?.role)) {
    return <Navigate to="/requests" replace />
  }

  return children
}