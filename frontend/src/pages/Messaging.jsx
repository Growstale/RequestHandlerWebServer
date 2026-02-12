import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { getShopContractorChats } from '@/api/shopContractorChatApi';
import { getMessageTemplates, createMessageTemplate, updateMessageTemplate, deleteMessageTemplate, sendMessage, sendMessageWithImage, getTemplateImageBlob } from '@/api/messagingApi';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { PlusCircle, Trash2, Edit, Send, BookCopy, AlertCircle, FileText, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import MessageTemplateForm from './MessageTemplateForm';
import RecipientSelector from './RecipientSelector'; 
import MessageTemplateSelectorModal from './MessageTemplateSelectorModal';

export default function Messaging() {
    const [activeTab, setActiveTab] = useState('send');
    const [message, setMessage] = useState('');
    const [imageFile, setImageFile] = useState(null);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [allChats, setAllChats] = useState([]);
    const [selectedChatIds, setSelectedChatIds] = useState(new Set());
    const [templates, setTemplates] = useState([]);
    const [sendError, setSendError] = useState('');
    const [sendSuccess, setSendSuccess] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isAlertOpen, setIsAlertOpen] = useState(false);
    const [isTemplateSelectorOpen, setIsTemplateSelectorOpen] = useState(false);
    const [currentTemplate, setCurrentTemplate] = useState(null);
    const [formApiError, setFormApiError] = useState(null);

    const loadInitialData = async () => {
        setLoading(true);
        try {
            const [chatsRes, templatesRes] = await Promise.all([
                getShopContractorChats({ size: 1000 }),
                getMessageTemplates()
            ]);
            setAllChats(chatsRes.data.content);
            setTemplates(templatesRes.data);
        } catch (err) {
            setError('Не удалось загрузить данные.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadInitialData();
    }, []);
    
    useEffect(() => {
        return () => {
            if (previewUrl) {
                URL.revokeObjectURL(previewUrl);
            }
        };
    }, [previewUrl]);

    const groupedChats = useMemo(() => allChats.reduce((acc, chat) => {
        const shopName = chat.shopName || 'Без магазина';
        if (!acc[shopName]) acc[shopName] = [];
        acc[shopName].push(chat);
        return acc;
    }, {}), [allChats]);

    const handleSelectChat = useCallback((chatId, checked) => setSelectedChatIds(prev => {
        const newSet = new Set(prev);
        if (checked) newSet.add(chatId);
        else newSet.delete(chatId);
        return newSet;
    }), []);

    const handleSelectAll = useCallback((checked) => {
        setSelectedChatIds(checked ? new Set(allChats.map(c => c.shopContractorChatID)) : new Set());
    }, [allChats]);

    const handleSelectTemplateFromModal = useCallback(async (template) => {
        if (template) {
            setMessage(template.message || '');
            setSelectedChatIds(new Set(template.recipientChatIds));
            
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            setPreviewUrl(null);
            setImageFile(null);
            setSendError('');

            if (template.hasImage) {
                try {
                    const response = await getTemplateImageBlob(template.messageID);
                    const blob = response.data;
                    const fileName = `template_image_${template.messageID}.jpg`;
                    const file = new File([blob], fileName, { type: blob.type });
                    
                    setImageFile(file);
                    setPreviewUrl(URL.createObjectURL(blob));
                } catch (error) {
                    console.error("Не удалось загрузить изображение из шаблона", error);
                    if (error.response?.status !== 404) {
                        setSendError("Не удалось загрузить изображение из шаблона.");
                    }
                }
            }
        }
        setIsTemplateSelectorOpen(false);
    }, [previewUrl]);


    const handleSendImageChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            setImageFile(file);
            if (previewUrl) URL.revokeObjectURL(previewUrl);
            setPreviewUrl(URL.createObjectURL(file));
        }
    };

    const handleClearSendImage = () => {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        setImageFile(null);
        setPreviewUrl(null);
        const fileInput = document.getElementById('send-image');
        if (fileInput) {
            fileInput.value = '';
        }
    };

    const handleSend = async () => {
        setSendError('');
        setSendSuccess('');
        if (!message.trim() && !imageFile) {
            setSendError('Текст сообщения и получатели не могут быть пустыми.');
            return;
        }

        try {
            if (imageFile) {
                const formData = new FormData();
                formData.append('message', message);
                const chatIdsString = Array.from(selectedChatIds).join(',');
                formData.append('recipientChatIds', chatIdsString);
                formData.append('image', imageFile);
                await sendMessageWithImage(formData);
            } else {
                await sendMessage({
                    message: message,
                    recipientChatIds: Array.from(selectedChatIds)
                });
            }
            setSendSuccess(`Сообщение успешно отправлено (в консоль) ${selectedChatIds.size} получателям.`);
            setMessage('');
            setSelectedChatIds(new Set());
            handleClearSendImage();
        } catch(err) {
            setSendError(err.response?.data || 'Ошибка при отправке.');
        }
    };
    
    const reloadTemplates = async () => {
        setLoading(true);
        try {
            const res = await getMessageTemplates();
            setTemplates(res.data);
        } catch(err) {
            setError('Не удалось обновить список шаблонов.');
        } finally {
            setLoading(false);
        }
    };

    const handleFormSubmit = async (formData) => {
        setFormApiError(null);
        try {
            if (currentTemplate) {
                await updateMessageTemplate(currentTemplate.messageID, formData);
            } else {
                await createMessageTemplate(formData);
            }
            setIsFormOpen(false);
            reloadTemplates();
        } catch(err) {
            setFormApiError(err.response?.data || 'Произошла ошибка.');
        }
    };
    
    const handleDeleteConfirm = async () => {
        if (!currentTemplate) return;
        try {
            await deleteMessageTemplate(currentTemplate.messageID);
            setIsAlertOpen(false);
            reloadTemplates();
        } catch(err) {
             console.error("Ошибка удаления:", err.response?.data);
             setIsAlertOpen(false);
        }
    };

    const openCreateForm = () => { setCurrentTemplate(null); setFormApiError(null); setIsFormOpen(true); };
    const openEditForm = (template) => { setCurrentTemplate(template); setFormApiError(null); setIsFormOpen(true); };
    const openDeleteAlert = (template) => { setCurrentTemplate(template); setIsAlertOpen(true); };


    return (
        <main className="container mx-auto p-6">
            <h1 className="text-3xl font-semibold mb-4">Управление рассылками</h1>
            
            <div className="flex border-b mb-6">
                <button 
                    onClick={() => setActiveTab('send')}
                    className={cn("py-2 px-4 -mb-px border-b-2", activeTab === 'send' ? 'border-blue-500 text-blue-600 font-semibold' : 'border-transparent text-gray-500 hover:text-gray-700')}
                >
                    <Send className="inline-block mr-2 h-4 w-4" /> Отправить сообщение
                </button>
                <button 
                    onClick={() => setActiveTab('templates')}
                    className={cn("py-2 px-4 -mb-px border-b-2", activeTab === 'templates' ? 'border-blue-500 text-blue-600 font-semibold' : 'border-transparent text-gray-500 hover:text-gray-700')}
                >
                    <BookCopy className="inline-block mr-2 h-4 w-4" /> Шаблоны
                </button>
            </div>

            {activeTab === 'send' && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                    <div className="lg:col-span-2 space-y-4">
                        <div className="space-y-2">
                            <Label>Вставить из шаблона</Label>
                            <Button variant="outline" className="w-full justify-start" onClick={() => setIsTemplateSelectorOpen(true)}>
                                <FileText className="mr-2 h-4 w-4" />
                                Выбрать шаблон...
                            </Button>
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="message-text">Текст сообщения</Label>
                            <Textarea 
                                id="message-text"
                                placeholder="Введите ваше сообщение здесь..."
                                value={message}
                                onChange={(e) => setMessage(e.target.value)}
                                rows={10}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label htmlFor="send-image">Прикрепить фото (необязательно)</Label>
                            <Input 
                                id="send-image" 
                                type="file" 
                                accept="image/jpeg, image/png" 
                                onChange={handleSendImageChange}
                                className="file:mr-4 file:py-1 file:px-2 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-primary file:text-primary-foreground hover:file:bg-primary/90"
                                style={{ 
                                    color: (previewUrl || imageFile) ? 'transparent' : 'inherit'
                                }}
                            />
                            {previewUrl && (
                                <div className="mt-2 relative w-40 h-40 border rounded-md p-1 bg-gray-50">
                                    <img src={previewUrl} alt="Предпросмотр" className="w-full h-full object-contain rounded"/>
                                    <Button type="button" variant="destructive" size="icon" className="absolute -top-2 -right-2 h-6 w-6 rounded-full" onClick={handleClearSendImage}>
                                        <X className="h-4 w-4" />
                                    </Button>
                                </div>
                            )}
                        </div>
                         {sendError && <p className="text-sm text-red-600 flex items-center gap-2"><AlertCircle className="h-4 w-4"/> {sendError}</p>}
                         {sendSuccess && <p className="text-sm text-green-600">{sendSuccess}</p>}
                        <Button onClick={handleSend} disabled={(!message.trim() && !imageFile) || selectedChatIds.size === 0}>
                            <Send className="mr-2 h-4 w-4" /> Отправить ({selectedChatIds.size})
                        </Button>
                    </div>
                    
                    <RecipientSelector
                        allChats={allChats}
                        groupedChats={groupedChats}
                        selectedChatIds={selectedChatIds}
                        onSelectChat={handleSelectChat}
                        onSelectAll={handleSelectAll}
                        loading={loading}
                    />
                </div>
            )}

            {activeTab === 'templates' && (
                <div>
                     <div className="flex justify-between items-center mb-4">
                        <p className="text-sm text-muted-foreground">Создавайте и управляйте шаблонами для быстрой отправки сообщений.</p>
                        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
                            <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать шаблон</Button></DialogTrigger>
                            <DialogContent className="max-w-4xl">
                                <DialogHeader>
                                <DialogTitle>{currentTemplate ? 'Редактировать шаблон' : 'Новый шаблон'}</DialogTitle>
                                </DialogHeader>
                                <MessageTemplateForm
                                    key={currentTemplate ? currentTemplate.messageID : 'new'}
                                    currentTemplate={currentTemplate}
                                    allChats={allChats}
                                    groupedChats={groupedChats}
                                    onSubmit={handleFormSubmit}
                                    onCancel={() => setIsFormOpen(false)}
                                    apiError={formApiError}
                                />
                            </DialogContent>
                        </Dialog>
                    </div>

                    {loading && <p>Загрузка...</p>}
                    {error && <p className="text-red-500">{error}</p>}
                    {!loading && !error && (
                        <div className="rounded-md border">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Название</TableHead>
                                        <TableHead>Сообщение</TableHead>
                                        <TableHead>Получателей</TableHead>
                                        <TableHead className="w-[120px]">Действия</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {templates.map(template => (
                                        <TableRow key={template.messageID}>
                                            <TableCell className="font-medium">{template.title}</TableCell>
                                            <TableCell className="text-muted-foreground">
                                                {template.message?.length > 80 ? template.message.substring(0, 80) + '...' : template.message}
                                            </TableCell>
                                            <TableCell>{template.recipientChatIds.length}</TableCell>
                                            <TableCell>
                                                <div className="flex gap-2">
                                                    <Button variant="outline" size="icon" onClick={() => openEditForm(template)}><Edit className="h-4 w-4" /></Button>
                                                    <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(template)}><Trash2 className="h-4 w-4" /></Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </div>
                    )}
                </div>
            )}
            
            <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        <AlertDialogDescription>Вы собираетесь удалить шаблон "{currentTemplate?.title}". Это действие нельзя будет отменить.</AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
            
            <MessageTemplateSelectorModal
                isOpen={isTemplateSelectorOpen}
                onClose={() => setIsTemplateSelectorOpen(false)}
                templates={templates}
                onSelectTemplate={handleSelectTemplateFromModal}
            />
        </main>
    );
}