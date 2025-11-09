import api from './axios';


export const getMessageTemplates = () => {
    return api.get('/api/admin/message-templates');
}

export const createMessageTemplate = (formData) => {
    return api.post('/api/admin/message-templates', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
}

export const updateMessageTemplate = (id, formData) => {
    return api.put(`/api/admin/message-templates/${id}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
}

export const deleteMessageTemplate = (id) => {
    return api.delete(`/api/admin/message-templates/${id}`);
}

export const getTemplateImageUrl = (templateId) => {
    return `${api.defaults.baseURL}/api/admin/message-templates/${templateId}/image?_=${new Date().getTime()}`;
}

export const getTemplateImageBlob = (templateId) => {
    return api.get(`/api/admin/message-templates/${templateId}/image`, {
        responseType: 'blob'
    });
}

export const deleteTemplateImage = (templateId) => {
    return api.delete(`/api/admin/message-templates/${templateId}/image`);
}


export const sendMessage = (data) => {
    return api.post('/api/admin/messaging/send', data);
}

export const sendMessageWithImage = (formData) => {
    return api.post('/api/admin/messaging/send-with-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
}