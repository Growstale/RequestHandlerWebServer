import api from './axios';

export const getMessageTemplates = () => {
    return api.get('/api/admin/message-templates');
}

export const createMessageTemplate = (data) => {
    return api.post('/api/admin/message-templates', data);
}

export const updateMessageTemplate = (id, data) => {
    return api.put(`/api/admin/message-templates/${id}`, data);
}

export const deleteMessageTemplate = (id) => {
    return api.delete(`/api/admin/message-templates/${id}`);
}

export const sendMessage = (data) => {
    return api.post('/api/admin/messaging/send', data);
}