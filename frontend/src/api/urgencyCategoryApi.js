import api from './axios'

export const getUrgencyCategories = () => {
  return api.get('/api/admin/urgency-categories');
}

export const updateUrgencyCategory = (urgencyId, data) => {
  return api.put(`/api/admin/urgency-categories/${urgencyId}`, data);
}