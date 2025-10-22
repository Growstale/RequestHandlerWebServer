import api from './axios'

const PAGE_SIZE = 40;

export const getShopContractorChats = (params = {}) => {
  const queryParams = new URLSearchParams();
  queryParams.append('page', params.page || 0);
  queryParams.append('size', PAGE_SIZE);

  if (params.sortConfig) {
    params.sortConfig.forEach(sort => {
      queryParams.append('sort', `${sort.field},${sort.direction}`);
    });
  }
  
  return api.get(`/api/admin/shop-contractor-chats?${queryParams.toString()}`);
}

export const createShopContractorChat = (data) => {
  return api.post('/api/admin/shop-contractor-chats', data);
}

export const updateShopContractorChat = (id, data) => {
  return api.put(`/api/admin/shop-contractor-chats/${id}`, data);
}

export const deleteShopContractorChat = (id) => {
  return api.delete(`/api/admin/shop-contractor-chats/${id}`);
}

export const checkShopContractorChatExists = (shopId, contractorId) => {
  return api.get(`/api/admin/shop-contractor-chats/exists?shopId=${shopId}&contractorId=${contractorId}`);
}