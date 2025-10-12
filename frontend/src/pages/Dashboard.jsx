import React from 'react'
import NavBar from '../components/NavBar'

export default function Dashboard() {
  return (
    <div>
      <main className="container mx-auto p-6">
        <h1 className="text-3xl font-semibold mb-4">Дашборд</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="p-4 bg-white shadow rounded">Добро пожаловать! Здесь будут ваши данные.</div>
          <div className="p-4 bg-white shadow rounded">Карточки, статистика и т.п.</div>
        </div>
      </main>
    </div>
  )
}