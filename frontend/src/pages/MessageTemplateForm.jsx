import React, { useState, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import RecipientSelector from './RecipientSelector';

const getInitialFormData = (template) => ({
    title: template?.title || '',
    message: template?.message || '',
    recipientChatIds: template?.recipientChatIds || [],
});

export default function MessageTemplateForm({ currentTemplate, allChats = [], groupedChats = {}, onSubmit, onCancel, apiError }) {
    const [formData, setFormData] = useState(() => getInitialFormData(currentTemplate));
    const [selectedChatIds, setSelectedChatIds] = useState(() => new Set(currentTemplate?.recipientChatIds || []));
    
    const isEditing = !!currentTemplate;

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };
    
    const handleSelectChat = useCallback((chatId, checked) => {
        setSelectedChatIds(prev => {
            const newSet = new Set(prev);
            if (checked) newSet.add(chatId);
            else newSet.delete(chatId);
            return newSet;
        });
    }, []);

    const handleSelectAll = useCallback((checked) => {
        setSelectedChatIds(checked ? new Set(allChats.map(c => c.shopContractorChatID)) : new Set());
    }, [allChats]);

    const handleSubmit = (e) => {
        e.preventDefault();
        onSubmit({
            ...formData,
            recipientChatIds: Array.from(selectedChatIds)
        });
    };

    return (
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 max-h-[80vh]">
            {apiError && <p className="col-span-2 text-red-600 p-2 bg-red-50 rounded-md">{apiError}</p>}
            
            <div className="space-y-4">
                 <div className="space-y-2">
                    <Label htmlFor="title">Название шаблона <span className="text-destructive">*</span></Label>
                    <Input id="title" name="title" value={formData.title} onChange={handleChange} required />
                </div>
                <div className="space-y-2">
                    <Label htmlFor="message">Текст сообщения</Label>
                    <Textarea id="message" name="message" value={formData.message} onChange={handleChange} rows={12} />
                </div>
            </div>

            <RecipientSelector
                allChats={allChats}
                groupedChats={groupedChats}
                selectedChatIds={selectedChatIds}
                onSelectChat={handleSelectChat}
                onSelectAll={handleSelectAll}
                loading={false}
            />

            <div className="md:col-span-2 flex justify-end gap-2 pt-4 border-t">
                <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
                <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
            </div>
        </form>
    );
}