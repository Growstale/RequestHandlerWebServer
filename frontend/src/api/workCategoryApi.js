import api from './axios'

const PAGE_SIZE = 40;

export const getWorkCategories = (params = {}) => {
  const queryParams = new URLSearchParams();

  queryParams.append('page', params.page || 0);
  queryParams.append('size', PAGE_SIZE);

  if (params.sortConfig) {
    params.sortConfig.forEach(sort => {
      queryParams.append('sort', `${sort.field},${sort.direction}`);
    });
  }
  
  const queryString = queryParams.toString();
  return api.get(`/api/admin/work-categories?${queryString}`);
}

export const createWorkCategory = (categoryData) => {
  return api.post('/api/admin/work-categories', categoryData)
}

export const updateWorkCategory = (categoryId, categoryData) => {
  return api.put(`/api/admin/work-categories/${categoryId}`, categoryData)
}

export const deleteWorkCategory = (categoryId) => {
  return api.delete(`/api/admin/work-categories/${categoryId}`)
}