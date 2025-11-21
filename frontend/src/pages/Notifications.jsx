import React, { useState, useEffect, useCallback } from 'react'
import { getNotifications, createNotification, updateNotification, deleteNotification } from '@/api/notificationApi'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue
} from '@/components/ui/select'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger
} from "@/components/ui/dialog"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { PlusCircle, Trash2, Edit, Bell, BellOff, Clock } from 'lucide-react'
import NotificationForm from './NotificationForm'
import Pagination from '@/components/Pagination'
import { cn } from '@/lib/utils'

export default function Notifications() {
  const [allNotifications, setAllNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [filterActive, setFilterActive] = useState('Все')
  const [currentPage, setCurrentPage] = useState(0)
  const [paginationData, setPaginationData] = useState({ totalPages: 0, totalItems: 0 })

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentNotification, setCurrentNotification] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const reloadNotifications = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const isActive = filterActive === 'Все' ? undefined : filterActive === 'Активные'
      const response = await getNotifications({ 
        isActive,
        page: currentPage 
      })
      setAllNotifications(response.data.content)
      setPaginationData({
        totalPages: response.data.totalPages,
        totalItems: response.data.totalItems,
      })
    } catch (err) {
      setError(err.response?.data || 'Не удалось загрузить уведомления')
    } finally {
      setLoading(false)
    }
  }, [filterActive, currentPage])

  useEffect(() => {
    reloadNotifications()
  }, [reloadNotifications])

  useEffect(() => {
    setCurrentPage(0)
  }, [filterActive])

  const handleFormSubmit = async (formData) => {
    setFormApiError(null)
    try {
      if (currentNotification) {
        await updateNotification(currentNotification.notificationID, formData)
      } else {
        await createNotification(formData)
      }
      setIsFormOpen(false)
      reloadNotifications()
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка')
    }
  }

  const handleDeleteConfirm = async () => {
    if (!currentNotification) return
    try {
      await deleteNotification(currentNotification.notificationID)
      setIsAlertOpen(false)
      reloadNotifications()
    } catch (err) {
      console.error("Ошибка удаления:", err.response?.data)
      setIsAlertOpen(false)
    }
  }

  const formatCronExpression = (cronExpression) => {
    const parts = cronExpression.split(' ')
    if (parts.length !== 5) return cronExpression

    const [minute, hour, dayOfMonth, month, dayOfWeek] = parts

    if (dayOfMonth === '*' && dayOfWeek === '*') {
      return `Ежедневно в ${hour}:${minute.padStart(2, '0')}`
    } else if (dayOfMonth === '*' && dayOfWeek !== '*') {
      const days = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб']
      return `Еженедельно (${days[parseInt(dayOfWeek)]}) в ${hour}:${minute.padStart(2, '0')}`
    } else if (dayOfMonth !== '*' && dayOfWeek === '*') {
      return `Ежемесячно (${dayOfMonth} число) в ${hour}:${minute.padStart(2, '0')}`
    }

    return cronExpression
  }

  const openCreateForm = () => { 
    setCurrentNotification(null)
    setFormApiError(null)
    setIsFormOpen(true)
  }
  
  const openEditForm = (notification) => { 
    setCurrentNotification(notification)
    setFormApiError(null)
    setIsFormOpen(true)
  }
  
  const openDeleteAlert = (notification) => { 
    setCurrentNotification(notification)
    setIsAlertOpen(true)
  }

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Управление уведомлениями</h1>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild>
            <Button onClick={openCreateForm}>
              <PlusCircle className="mr-2 h-4 w-4" /> Создать уведомление
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>
                {currentNotification ? 'Редактировать уведомление' : 'Новое уведомление'}
              </DialogTitle>
              <DialogDescription>
                {currentNotification 
                  ? `Вы редактируете уведомление "${currentNotification.title}"` 
                  : 'Заполните форму для создания нового уведомления.'
                }
              </DialogDescription>
            </DialogHeader>
            <NotificationForm 
              key={currentNotification ? currentNotification.notificationID : 'new-notification'}
              currentNotification={currentNotification} 
              onSubmit={handleFormSubmit} 
              onCancel={() => setIsFormOpen(false)}
              apiError={formApiError}
            />
          </DialogContent>
        </Dialog>
      </div>
      
      <p className="text-sm text-muted-foreground mb-4">
        Создавайте и управляйте автоматическими уведомлениями с настраиваемым расписанием.
      </p>

      <div className="flex items-center gap-4 mb-4">
        <Select value={filterActive} onValueChange={setFilterActive}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Фильтр по статусу" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="Все">Все</SelectItem>
            <SelectItem value="Активные">Активные</SelectItem>
            <SelectItem value="Неактивные">Неактивные</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {loading && <p>Загрузка...</p>}
      {error && <p className="text-red-500">{error}</p>}
      
      {!loading && !error && (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Статус</TableHead>
                <TableHead>Заголовок</TableHead>
                <TableHead>Сообщение</TableHead>
                <TableHead>Расписание</TableHead>
                <TableHead>Получатели</TableHead>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {allNotifications.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-8 text-gray-500">
                    Уведомления не найдены
                  </TableCell>
                </TableRow>
              ) : (
                allNotifications.map(notification => (
                  <TableRow key={notification.notificationID}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {notification.isActive ? (
                          <Bell className="h-4 w-4 text-green-600" />
                        ) : (
                          <BellOff className="h-4 w-4 text-gray-400" />
                        )}
                        <span className={cn(
                          "text-sm font-medium",
                          notification.isActive ? "text-green-600" : "text-gray-500"
                        )}>
                          {notification.isActive ? 'Активно' : 'Неактивно'}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="font-medium max-w-xs">
                      <div className="truncate" title={notification.title}>
                        {notification.title}
                      </div>
                    </TableCell>
                    <TableCell className="max-w-xs">
                      <div className="truncate" title={notification.message}>
                        {notification.message || '—'}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Clock className="h-4 w-4 text-gray-500" />
                        <span className="text-sm">
                          {formatCronExpression(notification.cronExpression)}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="text-sm text-gray-600">
                        {notification.recipientChatIds?.length || 0} получателей
                      </span>
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-2">
                        <Button 
                          variant="outline" 
                          size="icon" 
                          onClick={() => openEditForm(notification)}
                          title="Редактировать"
                        >
                          <Edit className="h-4 w-4" />
                        </Button>
                        <Button 
                          variant="destructive" 
                          size="icon" 
                          onClick={() => openDeleteAlert(notification)}
                          title="Удалить"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      <Pagination 
        currentPage={currentPage}
        totalPages={paginationData.totalPages}
        onPageChange={setCurrentPage}
      />

      <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
            <AlertDialogDescription>
              Вы собираетесь удалить уведомление <span className="font-bold">"{currentNotification?.title}"</span>. 
              Это действие нельзя будет отменить.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  )
}
