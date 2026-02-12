import api from './axios'

export const logApi = {
  getLogs: (params = {}) => {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.append('page', params.page)
    if (params.size !== undefined) queryParams.append('size', params.size)
    if (params.logLevel) queryParams.append('logLevel', params.logLevel)
    if (params.userID) queryParams.append('userID', params.userID)
    if (params.loggerName) queryParams.append('loggerName', params.loggerName)
    if (params.startDate) queryParams.append('startDate', params.startDate)
    if (params.endDate) queryParams.append('endDate', params.endDate)
    
    return api.get(`/api/admin/logs?${queryParams.toString()}`)
  },

  getLogStats: (params = {}) => {
    const queryParams = new URLSearchParams()
    if (params.startDate) queryParams.append('startDate', params.startDate)
    if (params.endDate) queryParams.append('endDate', params.endDate)
    
    return api.get(`/api/admin/logs/stats?${queryParams.toString()}`)
  }
}

