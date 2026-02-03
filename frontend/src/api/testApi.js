import api from './axios'

export const getTestConfig = () => api.get('/api/test/config');

export const setCheckInterval = (interval) => api.post('/api/test/interval', { interval });

export const setReminderCron = (cron) => api.post('/api/test/cron', { cron });

export const updateRequestDate = (requestId, date) => api.post(`/api/test/requests/${requestId}/date`, { date });

export const triggerCheckNow = () => api.post('/api/test/trigger-check');

export const triggerRemindNow = () => api.post('/api/test/trigger-remind');