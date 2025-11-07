import api from './axios'

const PAGE_SIZE = 40;

export const getRequests = (params = {}) => {
  const queryParams = new URLSearchParams();

  queryParams.append('page', params.page || 0);
  queryParams.append('size', PAGE_SIZE);
  queryParams.append('archived', params.archived || false);

  if (params.searchTerm) queryParams.append('searchTerm', params.searchTerm);
  if (params.shopId) queryParams.append('shopId', params.shopId);
  if (params.workCategoryId) queryParams.append('workCategoryId', params.workCategoryId);
  if (params.urgencyId) queryParams.append('urgencyId', params.urgencyId);
  if (params.contractorId) queryParams.append('contractorId', params.contractorId);
  if (params.overdue) queryParams.append('overdue', 'true');
  if (params.status) queryParams.append('status', params.status);

  if (params.sortConfig) {
    params.sortConfig.forEach(sort => {
      queryParams.append('sort', `${sort.field},${sort.direction}`);
    });
  }
  
  const queryString = queryParams.toString();
  return api.get(`/api/requests?${queryString}`);
}

export const createRequest = (data) => {
  return api.post('/api/requests', data);
}

export const updateRequest = (id, data) => {
  return api.put(`/api/requests/${id}`, data);
}

export const deleteRequest = (id) => {
  return api.delete(`/api/requests/${id}`);
}

export const getComments = (requestId) => {
  return api.get(`/api/requests/${requestId}/comments`);
}

export const addComment = (requestId, data) => {
  return api.post(`/api/requests/${requestId}/comments`, data);
}

export const getPhotos = (requestId) => {
    return api.get(`/api/requests/${requestId}/photos`, {
        responseType: 'arraybuffer' 
    });
}

export const uploadPhotos = (requestId, files) => {
    const formData = new FormData();
    files.forEach(file => {
        formData.append('files', file);
    });
    return api.post(`/api/requests/${requestId}/photos`, formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        },
    });
}

export const getPhotoIds = (requestId) => {
  return api.get(`/api/requests/${requestId}/photos/ids`);
}


export const getPhotoUrl = (photoId) => {
  return `${api.defaults.baseURL}/api/requests/photos/${photoId}`;
}

export const getPhotoBlob = (photoId) => {
  return api.get(`/api/requests/photos/${photoId}`, {
    responseType: 'blob' 
  });
}

export const deletePhoto = (photoId) => {
    return api.delete(`/api/requests/photos/${photoId}`);
}

export const restoreRequest = (id) => {
    return api.put(`/api/requests/${id}/restore`, {}); 
}

export const completeRequest = (id) => {
  return api.put(`/api/requests/${id}/complete`);
}

export const deleteComment = (commentId) => {
  return api.delete(`/api/requests/comments/${commentId}`);
}
