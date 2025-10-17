import api from './axios'

const PAGE_SIZE = 40;

export const getShops = (params = {}) => {
  const queryParams = new URLSearchParams();

  queryParams.append('page', params.page || 0);
  queryParams.append('size', PAGE_SIZE);

  if (params.sortConfig) {
    params.sortConfig.forEach(sort => {
      queryParams.append('sort', `${sort.field},${sort.direction}`);
    });
  }
  
  const queryString = queryParams.toString();
  return api.get(`/api/admin/shops?${queryString}`);
}

export const createShop = (shopData) => {
  return api.post('/api/admin/shops', shopData)
}

export const updateShop = (shopId, shopData) => {
  return api.put(`/api/admin/shops/${shopId}`, shopData)
}

export const deleteShop = (shopId) => {
  return api.delete(`/api/admin/shops/${shopId}`)
}