import React, { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getUrgencyDisplayName } from '@/lib/displayNames'

export default function UrgencyCategoryForm({ currentCategory, onSubmit, onCancel, apiError }) {
  const [days, setDays] = useState(currentCategory?.defaultDays || 1);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit({ defaultDays: Number(days) });
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit} className="grid gap-4 pt-4">
        {apiError && <div className="text-red-600 text-sm p-2 bg-red-50 rounded-md">{apiError}</div>}
        
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="urgencyName" className="text-right">Название</Label>
          <Input id="urgencyName" value={getUrgencyDisplayName(currentCategory.urgencyName)} className="col-span-3" disabled />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="defaultDays" className="text-right">Дней на выполнение <span className="text-destructive">*</span></Label>
          <Input 
            id="defaultDays" 
            name="defaultDays" 
            type="number"
            min="1"
            max="365"
            value={days} 
            onChange={(e) => setDays(e.target.value)} 
            className="col-span-3" 
            required 
          />
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
          <Button type="submit">Сохранить</Button>
        </div>
      </form>
    </div>
  )
}