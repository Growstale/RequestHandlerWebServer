import React, { useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './context/AuthProvider'
import ProtectedRoute from './components/ProtectedRoute'
import Sidebar from './components/Sidebar'
import { Menu } from 'lucide-react'

import Dashboard from './pages/Dashboard'
import Home from './pages/Home'
import Login from './pages/Login'
import Users from './pages/Users' 
import Shops from './pages/Shops' 
import WorkCategories from './pages/WorkCategories'
import UrgencyCategories from './pages/UrgencyCategories'
import Requests from './pages/Requests';
import ArchivedRequests from './pages/ArchivedRequests';
import ShopContractorChats from './pages/ShopContractorChats'; 
import Messaging from './pages/Messaging';
import Notifications from './pages/Notifications';
import Testing from './pages/Testing';

export default function App() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { user } = useAuth()

  return (
    <>
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      <div className="md:hidden fixed top-0 left-0 w-full h-14 bg-white border-b flex items-center px-4 z-30 shadow-sm">
          <button onClick={() => setSidebarOpen(true)} className="p-2 -ml-2">
              <Menu className="h-6 w-6 text-gray-700" />
          </button>
          <span className="ml-2 font-bold text-blue-600">MART INN FOOD</span>
      </div>

      <div className="pt-14 md:pt-0 md:pl-64 min-h-screen bg-background">
        <Routes>
          <Route 
            path="/" 
            element={
              <ProtectedRoute>
                {user?.role === 'RetailAdmin' ? <Dashboard /> : <Navigate to="/requests" replace />}
              </ProtectedRoute>
            } 
          />          
          <Route path="/login" element={<Login />} />
          <Route path="/dashboard" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Dashboard /></ProtectedRoute>} />
          <Route path="/users" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Users /></ProtectedRoute>} />
          <Route path="/shops" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Shops /></ProtectedRoute>} />
          <Route path="/shop-contractor-chats" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><ShopContractorChats /></ProtectedRoute>} />
          <Route path="/messaging" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Messaging /></ProtectedRoute>} />
          <Route path="/notifications" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Notifications /></ProtectedRoute>} />
          <Route path="/work-categories" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><WorkCategories /></ProtectedRoute>} />
          <Route path="/urgency-categories" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><UrgencyCategories /></ProtectedRoute>} />
          <Route path="/requests" element={ <ProtectedRoute><Requests /></ProtectedRoute> } />
          <Route path="/requests/archive" element={ <ProtectedRoute><ArchivedRequests /></ProtectedRoute> } />
          <Route path="/testing" element={<ProtectedRoute allowedRoles={['RetailAdmin']}><Testing /></ProtectedRoute>} />
        </Routes>        
      </div>
    </>
  )
}