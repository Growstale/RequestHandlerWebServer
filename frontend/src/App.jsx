import React, { useState } from 'react'
import { Routes, Route } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import Dashboard from './pages/Dashboard'
import Home from './pages/Home'
import Login from './pages/Login'
import Navbar from './components/NavBar'
import Sidebar from './components/Sidebar'
import Users from './pages/Users' 
import Shops from './pages/Shops' 
import WorkCategories from './pages/WorkCategories'
import UrgencyCategories from './pages/UrgencyCategories'
import Requests from './pages/Requests';
import ArchivedRequests from './pages/ArchivedRequests';
import ShopContractorChats from './pages/ShopContractorChats'; 

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
            path="/users"
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <Users />
              </ProtectedRoute>
            }
          />
          <Route
            path="/shops"
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <Shops />
              </ProtectedRoute>
            }
          />
          {/* V-- Новые маршруты --V */}
          <Route
            path="/shop-contractor-chats"
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <ShopContractorChats />
              </ProtectedRoute>
            }
          />
          <Route
            path="/work-categories"
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <WorkCategories />
              </ProtectedRoute>
            }
          />
          <Route
            path="/urgency-categories"
            element={
              <ProtectedRoute allowedRoles={['RetailAdmin']}>
                <UrgencyCategories />
              </ProtectedRoute>
            }
          />
          <Route
            path="/requests"
            element={ <ProtectedRoute><Requests /></ProtectedRoute> }
          />
          <Route
              path="/requests/archive"
              element={ <ProtectedRoute><ArchivedRequests /></ProtectedRoute> }
          />
        </Routes>        
      </div>
    </>
  )
}