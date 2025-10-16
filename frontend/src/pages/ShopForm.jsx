
import React, { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const getInitialFormData = (shop) => {
  return {
    shopName: shop?.shopName || '',
    address: shop?.address || '',
    email: shop?.email || '',
    telegramID: shop?.telegramID?.toString() || '',
    userID: shop?.userID || null,
  }
}

export default function ShopForm({ currentShop, users = [], onSubmit, onCancel, apiError }) {
  const [formData, setFormData] = useState(() => getInitialFormData(currentShop))
  const isEditing = !!currentShop

  const handleChange = (e) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
  }

  const handleUserChange = (value) => {
    const userId = value === '__NONE__' ? null : parseInt(value, 10);
    setFormData(prev => ({ ...prev, userID: userId }));
  }
  
  const handleSubmit = (e) => {
    e.preventDefault()
    onSubmit(formData)
  }

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="grid gap-4 pt-4">
        {apiError && <div className="text-red-600 text-sm p-2 bg-red-50 rounded-md">{apiError}</div>}
        
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="shopName" className="text-right">Название <span className="text-destructive">*</span></Label>
          <Input id="shopName" name="shopName" value={formData.shopName} onChange={handleChange} className="col-span-3" required />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="address" className="text-right">Адрес</Label>
          <Input id="address" name="address" value={formData.address} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="email" className="text-right">Email</Label>
          <Input id="email" name="email" type="email" value={formData.email} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="telegramID" className="text-right">Telegram ID</Label>
          <Input id="telegramID" name="telegramID" value={formData.telegramID} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="userID" className="text-right">Пользователь</Label>
          <Select
            value={formData.userID?.toString() || "__NONE__"}
            onValueChange={handleUserChange}
          >
            <SelectTrigger className="col-span-3">
              <SelectValue placeholder="Не назначен" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__NONE__">Не назначен</SelectItem>
              {users.map(user => (
                <SelectItem key={user.userID} value={user.userID.toString()}>{user.login}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
          <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
        </div>
      </form>
    </div>
  )
}