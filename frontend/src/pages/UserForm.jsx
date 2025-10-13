import React, { useState } from 'react'
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

const getInitialFormData = (user) => {
  if (user) {
    return {
      login: user.login || '',
      password: '',
      roleName: user.roleName || 'Contractor',
      fullName: user.fullName || '',
      contactInfo: user.contactInfo || '',
      telegramID: user.telegramID?.toString() || '',
    };
  }

  return {
    login: '',
    password: '',
    roleName: 'Contractor',
    fullName: '',
    contactInfo: '',
    telegramID: '',
  };
};

export default function UserForm({ currentUser, onSubmit, onCancel, apiError, roles = [] }) {
  
  const [formData, setFormData] = useState(() => getInitialFormData(currentUser));

  const isEditing = !!currentUser;


  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleRoleChange = (value) => {
    setFormData(prev => ({ ...prev, roleName: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const dataToSend = { ...formData };
    if (isEditing && !dataToSend.password) {
      delete dataToSend.password;
    }
    onSubmit(dataToSend);
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground pt-2">
        Поля, отмеченные <span className="text-destructive font-bold">*</span>, обязательны для заполнения.
      </p>

      <form onSubmit={handleSubmit} className="grid gap-4">
        {apiError && <div className="text-red-600 text-sm p-2 bg-red-50 rounded-md">{apiError}</div>}
        
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="login" className="text-right">
            Логин <span className="text-destructive">*</span>
          </Label>
          <Input id="login" name="login" value={formData.login} onChange={handleChange} className="col-span-3" required disabled={isEditing} />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="password" className="text-right">
            {isEditing ? 'Новый пароль' : <>Пароль <span className="text-destructive">*</span></>}
          </Label>
          <Input id="password" name="password" type="password" placeholder={isEditing ? 'Оставьте пустым, чтобы не менять' : ''} value={formData.password} onChange={handleChange} className="col-span-3" required={!isEditing} />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="fullName" className="text-right">ФИО</Label>
          <Input id="fullName" name="fullName" value={formData.fullName} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="roleName" className="text-right">
            Роль <span className="text-destructive">*</span>
          </Label>
          <Select
            value={formData.roleName}
            onValueChange={handleRoleChange}
          >
            <SelectTrigger className="col-span-3">
              <SelectValue placeholder="Выберите роль" />
            </SelectTrigger>
            <SelectContent>
              {roles.map(role => <SelectItem key={role.roleID} value={role.roleName}>{role.roleName}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="contactInfo" className="text-right">Контакт. инфо</Label>
          <Input id="contactInfo" name="contactInfo" value={formData.contactInfo} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor="telegramID" className="text-right">Telegram ID</Label>
          <Input id="telegramID" name="telegramID" value={formData.telegramID} onChange={handleChange} className="col-span-3" />
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
          <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
        </div>
      </form>
    </div>
  )
}