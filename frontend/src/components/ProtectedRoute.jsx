import React from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthProvider'

export default function ProtectedRoute({ children }) {
  const { accessToken, loading } = useAuth()
  if (loading) return <div className="p-8">Загрузка...</div>
  if (!accessToken) return <Navigate to="/login" replace />
  return children
}