import React, { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const getInitialFormData = (category) => {
  return {
    workCategoryName: category?.workCategoryName || '',
  }
}

export default function WorkCategoryForm({ currentCategory, onSubmit, onCancel, apiError }) {
  const [formData, setFormData] = useState(() => getInitialFormData(currentCategory))
  const isEditing = !!currentCategory

  const handleChange = (e) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
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
          <Label htmlFor="workCategoryName" className="text-right">Название <span className="text-destructive">*</span></Label>
          <Input id="workCategoryName" name="workCategoryName" value={formData.workCategoryName} onChange={handleChange} className="col-span-3" required />
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
          <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
        </div>
      </form>
    </div>
  )
}