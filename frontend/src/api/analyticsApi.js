import api from './axios'

export const getDashboardStats = () => {
  return api.get('/api/analytics/stats')
}