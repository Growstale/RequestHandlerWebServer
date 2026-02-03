import api from './axios'

const PAGE_SIZE = 40;

export const getNotifications = (params = {}) => {
  const queryParams = new URLSearchParams()

  queryParams.append('page', params.page || 0);
  queryParams.append('size', PAGE_SIZE);

  if (params.isActive !== undefined && params.isActive !== null) {
    queryParams.append('isActive', params.isActive)
  }

  const queryString = queryParams.toString()
  return api.get(`/api/admin/notifications?${queryString}`)
}

export const getNotificationById = (notificationId) => {
  return api.get(`/api/admin/notifications/${notificationId}`)
}

export const createNotification = (notificationData) => {
  return api.post('/api/admin/notifications', notificationData)
}

export const updateNotification = (notificationId, notificationData) => {
  return api.put(`/api/admin/notifications/${notificationId}`, notificationData)
}

export const deleteNotification = (notificationId) => {
  return api.delete(`/api/admin/notifications/${notificationId}`)
}

export const cronPresets = {
  daily: { label: 'Ежедневно', value: '0 9 * * *' },
  weekly: { label: 'Еженедельно (понедельник)', value: '0 9 * * 1' },
  monthly: { label: 'Ежемесячно (1 число)', value: '0 9 1 * *' }
}

export const generateCronExpression = (type, hour = 9, minute = 0, dayOfWeek = 1, dayOfMonth = 1) => {
  switch (type) {
    case 'daily':
      return `${minute} ${hour} * * *`
    case 'weekly':
      return `${minute} ${hour} * * ${dayOfWeek}`
    case 'monthly':
      return `${minute} ${hour} ${dayOfMonth} * *`
    default:
      return '0 9 * * *'
  }
}

export const parseCronExpression = (cronExpression) => {
  if (!cronExpression) return null;

  const parts = cronExpression.trim().split(/\s+/)
  
  if (parts.length !== 5) return null

  const [minute, hour, dayOfMonth, month, dayOfWeek] = parts

  if (dayOfMonth === '*' && dayOfWeek === '*') {
    return { type: 'daily', hour: parseInt(hour), minute: parseInt(minute) }
  } else if (dayOfMonth === '*' && dayOfWeek !== '*') {
    return { type: 'weekly', hour: parseInt(hour), minute: parseInt(minute), dayOfWeek: parseInt(dayOfWeek) }
  } else if (dayOfMonth !== '*' && dayOfWeek === '*') {
    return { type: 'monthly', hour: parseInt(hour), minute: parseInt(minute), dayOfMonth: parseInt(dayOfMonth) }
  }

  return { type: 'custom', cronExpression }
}
