import api from './axios'

export const getUsers = (params = {}) => {
  const queryParams = new URLSearchParams()

  if (params.role && params.role !== 'Все') {
    queryParams.append('role', params.role)
  }

  if (params.sortConfig) {
    params.sortConfig.forEach(sort => {
      queryParams.append('sort', `${sort.field},${sort.direction}`)
    })
  }
  
  const queryString = queryParams.toString()
  return api.get(`/api/admin/users${queryString ? `?${queryString}` : ''}`)
}

export const createUser = (userData) => {
  return api.post('/api/admin/users', userData)
}

export const updateUser = (userId, userData) => {
  return api.put(`/api/admin/users/${userId}`, userData)
}

export const deleteUser = (userId) => {
  return api.delete(`/api/admin/users/${userId}`)
}


export const getRoles = () => api.get('/api/roles');