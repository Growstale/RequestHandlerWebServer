import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthProvider'
import { Input } from '@/components/ui/input' // <-- Импортируем Input
import { Button } from '@/components/ui/button' // <-- Импортируем Button
// Для селекта можно установить shadcn-ui/select, но для простоты оставим стилизованный select
// import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

export default function Register() {
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [roleName, setRoleName] = useState('Contractor')
  const [error, setError] = useState(null)
  const { register } = useAuth()
  const navigate = useNavigate()

  const onSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    try {
      await register(login, password, roleName)
      navigate('/login')
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Ошибка регистрации')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-indigo-50 to-white p-4">
      <div className="w-full max-w-md bg-white rounded-lg shadow p-8">
        <h2 className="text-2xl font-bold mb-6 text-center">Регистрация</h2>
        {error && <div className="text-red-600 mb-4 text-center text-sm">{error}</div>}
        <form onSubmit={onSubmit} className="space-y-4">
          <Input
            value={login}
            onChange={e => setLogin(e.target.value)}
            placeholder="Логин"
            required
          />
          <Input
            value={password}
            onChange={e => setPassword(e.target.value)}
            type="password"
            placeholder="Пароль"
            required
          />
          {/* Для лучшего вида стоит использовать компонент Select от shadcn-ui */}
          <select
            value={roleName}
            onChange={e => setRoleName(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm"
          >
            <option>Contractor</option>
            <option>StoreManager</option>
            <option>RetailAdmin</option>
          </select>
          <Button type="submit" className="w-full mt-2">
            Зарегистрироваться
          </Button>
        </form>
      </div>
    </div>
  )
}