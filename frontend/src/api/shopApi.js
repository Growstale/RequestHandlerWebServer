import api from './axios'

export const getShops = (sortConfig = []) => { // <<< 1. ПРИНИМАТЬ sortConfig
  const params = new URLSearchParams();
  sortConfig.forEach(sort => {
    params.append('sort', `${sort.field},${sort.direction}`);
  });
  
  const queryString = params.toString();
  return api.get(`/api/admin/shops${queryString ? `?${queryString}` : ''}`); // <<< 3. ДОБАВИТЬ К URL
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