import React, { useState, useEffect } from 'react'
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { generateCronExpression, parseCronExpression } from '@/api/notificationApi'
import { getShopContractorChats } from '@/api/shopContractorChatApi'

export default function NotificationForm({ currentNotification, onSubmit, onCancel, apiError }) {
  const [formData, setFormData] = useState({
    title: '',
    message: '',
    isActive: true,
    scheduleType: 'daily',
    hour: 9,
    minute: 0,
    dayOfWeek: 1,
    dayOfMonth: 1,
    customCron: '',
    recipientChatIds: []
  })

  const [availableChats, setAvailableChats] = useState([])
  const [loading, setLoading] = useState(false)
  const [errors, setErrors] = useState({})

  useEffect(() => {
    const fetchChats = async () => {
      try {
        const response = await getShopContractorChats({ size: 1000 })
        setAvailableChats(response.data.content || [])
      } catch (error) {
        console.error('Ошибка загрузки чатов:', error)
      }
    }
    fetchChats()
  }, [])

  useEffect(() => {
    if (currentNotification) {
      const cronData = parseCronExpression(currentNotification.cronExpression)
      setFormData({
        title: currentNotification.title || '',
        message: currentNotification.message || '',
        isActive: currentNotification.isActive,
        scheduleType: cronData?.type || 'custom',
        hour: cronData?.hour || 9,
        minute: cronData?.minute || 0,
        dayOfWeek: cronData?.dayOfWeek || 1,
        dayOfMonth: cronData?.dayOfMonth || 1,
        customCron: cronData?.type === 'custom' ? currentNotification.cronExpression : '',
        recipientChatIds: currentNotification.recipientChatIds || []
      })
    }
  }, [currentNotification])

  const handleInputChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }))
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: null }))
    }
  }

  const handleRecipientToggle = (chatId) => {
    setFormData(prev => ({
      ...prev,
      recipientChatIds: prev.recipientChatIds.includes(chatId)
        ? prev.recipientChatIds.filter(id => id !== chatId)
        : [...prev.recipientChatIds, chatId]
    }))
  }

  const handleSelectAllRecipients = () => {
    setFormData(prev => {
      const allIds = availableChats.map(chat => chat.shopContractorChatID)
      const isAllSelected = prev.recipientChatIds.length === allIds.length && allIds.every(id => prev.recipientChatIds.includes(id))
      return {
        ...prev,
        recipientChatIds: isAllSelected ? [] : allIds
      }
    })
  }

  const isAllRecipientsSelected = availableChats.length > 0 &&
    formData.recipientChatIds.length === availableChats.length &&
    availableChats.every(chat => formData.recipientChatIds.includes(chat.shopContractorChatID))

  const validateForm = () => {
    const newErrors = {}

    if (!formData.title.trim()) {
      newErrors.title = 'Заголовок обязателен'
    }

    if (formData.scheduleType === 'custom' && !formData.customCron.trim()) {
      newErrors.customCron = 'Cron выражение обязательно'
    }

    if (formData.hour < 0 || formData.hour > 23) {
      newErrors.hour = 'Час должен быть от 0 до 23'
    }

    if (formData.minute < 0 || formData.minute > 59) {
      newErrors.minute = 'Минута должна быть от 0 до 59'
    }

    if (formData.scheduleType === 'weekly' && (formData.dayOfWeek < 0 || formData.dayOfWeek > 6)) {
      newErrors.dayOfWeek = 'День недели должен быть от 0 (воскресенье) до 6 (суббота)'
    }

    if (formData.scheduleType === 'monthly' && (formData.dayOfMonth < 1 || formData.dayOfMonth > 31)) {
      newErrors.dayOfMonth = 'День месяца должен быть от 1 до 31'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    
    if (!validateForm()) {
      return
    }

    setLoading(true)

    try {
      const cronExpression = formData.scheduleType === 'custom' 
        ? formData.customCron
        : generateCronExpression(
            formData.scheduleType, 
            formData.hour, 
            formData.minute, 
            formData.dayOfWeek, 
            formData.dayOfMonth
          )

      const submitData = {
        title: formData.title,
        message: formData.message,
        cronExpression,
        isActive: formData.isActive,
        recipientChatIds: formData.recipientChatIds
      }

      await onSubmit(submitData)
    } catch (error) {
      console.error('Ошибка отправки формы:', error)
    } finally {
      setLoading(false)
    }
  }

  const dayOfWeekOptions = [
    { value: 0, label: 'Воскресенье' },
    { value: 1, label: 'Понедельник' },
    { value: 2, label: 'Вторник' },
    { value: 3, label: 'Среда' },
    { value: 4, label: 'Четверг' },
    { value: 5, label: 'Пятница' },
    { value: 6, label: 'Суббота' }
  ]

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <Label htmlFor="title">Заголовок *</Label>
        <Input
          id="title"
          value={formData.title}
          onChange={(e) => handleInputChange('title', e.target.value)}
          placeholder="Введите заголовок уведомления"
          className={errors.title ? 'border-red-500' : ''}
        />
        {errors.title && <p className="text-red-500 text-sm mt-1">{errors.title}</p>}
      </div>

      <div>
        <Label htmlFor="message">Сообщение</Label>
        <Textarea
          id="message"
          value={formData.message}
          onChange={(e) => handleInputChange('message', e.target.value)}
          placeholder="Введите текст уведомления"
          rows={3}
        />
      </div>

      <div>
        <Label htmlFor="scheduleType">Тип расписания</Label>
        <Select value={formData.scheduleType} onValueChange={(value) => handleInputChange('scheduleType', value)}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="daily">Ежедневно</SelectItem>
            <SelectItem value="weekly">Еженедельно</SelectItem>
            <SelectItem value="monthly">Ежемесячно</SelectItem>
            <SelectItem value="custom">Пользовательское</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {formData.scheduleType !== 'custom' && (
        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label htmlFor="hour">Час</Label>
            <Input
              id="hour"
              type="number"
              min="0"
              max="23"
              value={formData.hour}
              onChange={(e) => handleInputChange('hour', parseInt(e.target.value))}
              className={errors.hour ? 'border-red-500' : ''}
            />
            {errors.hour && <p className="text-red-500 text-sm mt-1">{errors.hour}</p>}
          </div>
          <div>
            <Label htmlFor="minute">Минута</Label>
            <Input
              id="minute"
              type="number"
              min="0"
              max="59"
              value={formData.minute}
              onChange={(e) => handleInputChange('minute', parseInt(e.target.value))}
              className={errors.minute ? 'border-red-500' : ''}
            />
            {errors.minute && <p className="text-red-500 text-sm mt-1">{errors.minute}</p>}
          </div>
        </div>
      )}

      {formData.scheduleType === 'weekly' && (
        <div>
          <Label htmlFor="dayOfWeek">День недели</Label>
          <Select value={formData.dayOfWeek.toString()} onValueChange={(value) => handleInputChange('dayOfWeek', parseInt(value))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {dayOfWeekOptions.map(option => (
                <SelectItem key={option.value} value={option.value.toString()}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}

      {formData.scheduleType === 'monthly' && (
        <div>
          <Label htmlFor="dayOfMonth">День месяца</Label>
          <Input
            id="dayOfMonth"
            type="number"
            min="1"
            max="31"
            value={formData.dayOfMonth}
            onChange={(e) => handleInputChange('dayOfMonth', parseInt(e.target.value))}
            className={errors.dayOfMonth ? 'border-red-500' : ''}
          />
          {errors.dayOfMonth && <p className="text-red-500 text-sm mt-1">{errors.dayOfMonth}</p>}
        </div>
      )}

      {formData.scheduleType === 'custom' && (
        <div>
          <Label htmlFor="customCron">Cron выражение *</Label>
          <Input
            id="customCron"
            value={formData.customCron}
            onChange={(e) => handleInputChange('customCron', e.target.value)}
            placeholder="* * * * * (минута час день_месяца месяц день_недели)"
            className={errors.customCron ? 'border-red-500' : ''}
          />
          {errors.customCron && <p className="text-red-500 text-sm mt-1">{errors.customCron}</p>}
          <p className="text-sm text-gray-500 mt-1">
            Формат: минута час день_месяца месяц день_недели (0-6 = SUN-SAT)
          </p>
        </div>
      )}

      <div>
        <div className="flex items-center justify-between">
          <Label>Получатели</Label>
          {availableChats.length > 0 && (
            <Button type="button" variant="ghost" className="text-xs" onClick={handleSelectAllRecipients}>
              {isAllRecipientsSelected ? 'Снять выбор' : 'Выбрать все'}
            </Button>
          )}
        </div>
        <div className="max-h-40 overflow-y-auto border rounded p-2 space-y-2">
          {availableChats.length === 0 ? (
            <p className="text-gray-500 text-sm">Нет доступных чатов</p>
          ) : (
            availableChats.map(chat => (
              <label key={chat.shopContractorChatID} className="flex items-center space-x-2 cursor-pointer hover:bg-gray-50 p-1 rounded">
                <input
                  type="checkbox"
                  checked={formData.recipientChatIds.includes(chat.shopContractorChatID)}
                  onChange={() => handleRecipientToggle(chat.shopContractorChatID)}
                  className="mt-0.5"
                />
                <span className="text-sm">
                  <span className="font-semibold">{chat.shopName}</span> - {chat.contractorLogin || 'Без подрядчика'} <span className="text-gray-400 text-xs">(ID: {chat.shopContractorChatID})</span>
                </span>
              </label>
            ))
          )}
        </div>
      </div>

      <div className="flex items-center space-x-2">
        <input
          type="checkbox"
          id="isActive"
          checked={formData.isActive}
          onChange={(e) => handleInputChange('isActive', e.target.checked)}
        />
        <Label htmlFor="isActive">Активно</Label>
      </div>

      {apiError && (
        <div className="text-red-500 text-sm">
          {typeof apiError === 'string' ? apiError : 'Произошла ошибка при сохранении'}
        </div>
      )}

      <div className="flex gap-2 pt-4">
        <Button type="submit" disabled={loading}>
          {loading ? 'Сохранение...' : (currentNotification ? 'Обновить' : 'Создать')}
        </Button>
        <Button type="button" variant="outline" onClick={onCancel}>
          Отмена
        </Button>
      </div>
    </form>
  )
}