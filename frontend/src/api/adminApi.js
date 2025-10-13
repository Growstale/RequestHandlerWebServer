import api from './axios'

export const getUsers = (params = {}) => {
  const queryParams = new URLSearchParams()

  if (params.role) {
    queryParams.append('role', params.role)
  }
  
  return api.get(`/api/admin/users?${queryParams.toString()}`)
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