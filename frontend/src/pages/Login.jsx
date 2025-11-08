import React, { useState, useEffect } from 'react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthProvider'
import { Label } from "@/components/ui/label"

export default function Login() {
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const { login: doLogin, accessToken, loading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from?.pathname || "/"

  useEffect(() => {
    if (!loading && accessToken) {
      navigate(from, { replace: true });
    }
  }, [accessToken, loading, navigate])

  const onSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    try {
      await doLogin(login, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.response?.data || err.message || 'Ошибка')
    }
  }

  if (loading) {
      return null; 
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-50 to-white">
      <div className="w-full max-w-md bg-white rounded-lg shadow p-8">
        <h2 className="text-2xl font-bold mb-6 text-center">Вход</h2>
        {error && <div className="text-red-600 mb-4">{error}</div>}
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-1">
            <Label htmlFor="login">Логин</Label>
            <Input
              id="login"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              placeholder="Ваш логин"
              required
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="password">Пароль</Label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Ваш пароль"
              required
            />
          </div>
          <Button type="submit" className="w-full mt-2">
            Войти
          </Button>
        </form>
      </div>
    </div>
  )
}
