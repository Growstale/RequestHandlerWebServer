import React, { useState, useCallback, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { X } from 'lucide-react';
import RecipientSelector from './RecipientSelector';
import { getTemplateImageUrl, deleteTemplateImage, getTemplateImageBlob } from '@/api/messagingApi';

const getInitialFormData = (template) => ({
    title: template?.title || '',
    message: template?.message || '',
    recipientChatIds: template?.recipientChatIds || [],
});

export default function MessageTemplateForm({ currentTemplate, allChats = [], groupedChats = {}, onSubmit, onCancel, apiError }) {
    const [formData, setFormData] = useState(() => getInitialFormData(currentTemplate));
    const [selectedChatIds, setSelectedChatIds] = useState(() => new Set(currentTemplate?.recipientChatIds || []));
    const [imageFile, setImageFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [existingImage, setExistingImage] = useState(currentTemplate?.hasImage);
    const [imageKey, setImageKey] = useState(Date.now());
    
    const isEditing = !!currentTemplate;

    useEffect(() => {
        let objectUrl;
        if (isEditing && currentTemplate?.hasImage) {
            getTemplateImageBlob(currentTemplate.messageID)
                .then(response => {
                    objectUrl = URL.createObjectURL(response.data);
                    setPreviewUrl(objectUrl);
                })
                .catch(err => {
                    console.error("Не удалось загрузить изображение шаблона:", err);
                });
        }
        
        return () => {
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
        };
    }, [currentTemplate, isEditing]);
    
    useEffect(() => {
        return () => {
            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
            }
        };
    }, [previewUrl]);

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (previewUrl) URL.revokeObjectURL(previewUrl); 

        if (file) {
            setImageFile(file);
            setPreviewUrl(URL.createObjectURL(file));
            setExistingImage(false); 
        } else {
            setImageFile(null);
            setPreviewUrl(null);
        }
        e.target.value = null;
    };
    
    const handleDeleteImage = async () => {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        setImageFile(null);
        setPreviewUrl(null);
        
        if (isEditing && existingImage) {
             try {
                await deleteTemplateImage(currentTemplate.messageID);
                setExistingImage(false);
             } catch (error) {
                console.error("Ошибка при попытке удаления изображения:", error);
            }
        }
    };

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
        const finalFormData = new FormData();
        finalFormData.append('title', formData.title);
        finalFormData.append('message', formData.message || '');
        
        const chatIdsString = Array.from(selectedChatIds).join(',');
        finalFormData.append('recipientChatIds', chatIdsString);

        if (imageFile) {
            finalFormData.append('image', imageFile);
        }
        
        onSubmit(finalFormData);
    };

    return (
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 max-h-[80vh] overflow-y-auto pr-2">
            {apiError && <p className="col-span-2 text-red-600 p-2 bg-red-50 rounded-md">{apiError}</p>}
            
            <div className="space-y-4 flex flex-col">
                 <div className="space-y-2">
                    <Label htmlFor="title">Название шаблона <span className="text-destructive">*</span></Label>
                    <Input id="title" name="title" value={formData.title} onChange={handleChange} required />
                </div>
                <div className="space-y-2 flex-grow flex flex-col">
                    <Label htmlFor="message">Текст сообщения</Label>
                    <Textarea id="message" name="message" value={formData.message} onChange={handleChange} className="flex-grow min-h-[150px]" />
                </div>
                <div className="space-y-2">
                    <Label htmlFor="image">Изображение (необязательно)</Label>
                    <Input id="image" type="file" accept="image/jpeg, image/png" onChange={handleFileChange} />
                    {(previewUrl || (isEditing && existingImage)) && (
                        <div className="mt-2 relative w-40 h-40 border rounded-md p-1 bg-gray-50">
                            <img 
                                key={imageKey}
                                src={previewUrl}
                                alt="Предпросмотр" 
                                className="w-full h-full object-contain rounded"
                                onError={() => setExistingImage(false)}
                            />
                            <Button
                                type="button"
                                variant="destructive"
                                size="icon"
                                className="absolute -top-2 -right-2 h-6 w-6 rounded-full"
                                onClick={handleDeleteImage}
                            >
                                <X className="h-4 w-4" />
                            </Button>
                        </div>
                    )}
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

            <div className="md:col-span-2 flex justify-end gap-2 pt-4 border-t mt-4">
                <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
                <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
            </div>
        </form>
    );
}