import React from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'

export default function Home() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 p-6">
      <h1 className="text-4xl font-bold text-blue-700 mb-6">
        Добро пожаловать в сервис управления заявками
      </h1>
      <p className="text-gray-600 text-lg max-w-md text-center mb-6">
        Текст текст текст текст
      </p>
    </div>
  )
}
