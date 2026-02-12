import api from './axios'

export const auditApi = {
  getAuditLogs: (params = {}) => {
    const queryParams = new URLSearchParams()
    if (params.page !== undefined) queryParams.append('page', params.page)
    if (params.size !== undefined) queryParams.append('size', params.size)
    if (params.userID) queryParams.append('userID', params.userID)
    if (params.tableName) queryParams.append('tableName', params.tableName)
    if (params.action) queryParams.append('action', params.action)
    if (params.startDate) queryParams.append('startDate', params.startDate)
    if (params.endDate) queryParams.append('endDate', params.endDate)
    
    return api.get(`/api/admin/audit?${queryParams.toString()}`)
  },

  getAuditStats: (params = {}) => {
    const queryParams = new URLSearchParams()
    if (params.startDate) queryParams.append('startDate', params.startDate)
    if (params.endDate) queryParams.append('endDate', params.endDate)
    
    return api.get(`/api/admin/audit/stats?${queryParams.toString()}`)
  }
}

