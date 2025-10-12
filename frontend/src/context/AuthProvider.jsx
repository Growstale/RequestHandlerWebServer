import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'
import api, { attachAccessToken, setOnRefreshFail } from '../api/axios'
import { logger } from '../lib/logger'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [accessToken, setAccessToken] = useState(null)
  const [loading, setLoading] = useState(true)

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } catch (e) {
      logger.warn('Logout failed on server:', e)
    } finally {
      setAccessToken(null)
      setUser(null)
      delete api.defaults.headers.common['Authorization']
    }
  }, [])

  useEffect(() => {
    setOnRefreshFail(() => logout());
  }, [logout]);

  useEffect(() => {
    attachAccessToken(accessToken)
  }, [accessToken])

  useEffect(() => {
    const initializeAuth = async () => {
      try {
        const refreshResp = await api.post('/api/auth/refresh')
        const token = refreshResp.data.accessToken
        setAccessToken(token)
        attachAccessToken(token)
        const whoamiResp = await api.get('/api/user/whoami')
        setUser({
          username: whoamiResp.data.login,
          role: whoamiResp.data.role
        })
      } catch (e) {
        logger.info('User is not authenticated on initial load')
        setAccessToken(null)
        setUser(null)
      } finally {
        setLoading(false)
      }
    }

    initializeAuth()
  }, [])


  const login = async (login, password) => {
    const resp = await api.post('/api/auth/login', { login, password })
    setAccessToken(resp.data.accessToken)
    attachAccessToken(resp.data.accessToken)
    try {
      const whoamiResp = await api.get('/api/user/whoami')
      setUser({
        username: whoamiResp.data.login,
        role: whoamiResp.data.role
      })
    } catch (e) {
      logger.error('AuthProvider.login', error)
      setUser(null)
    }

    return resp
  }

  const register = async (loginVal, password, roleName = 'StoreManager') => {
    const resp = await api.post('/api/auth/register', { login: loginVal, password, roleName })
    return resp
  }

  return (
    <AuthContext.Provider value={{ user, setUser, accessToken, setAccessToken, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)