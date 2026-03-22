import React, { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { getUrgencyDisplayName } from '@/lib/displayNames';
import { getShopContractorChats } from '@/api/shopContractorChatApi';
import { Loader2, Send, AlertTriangle, X, Paperclip } from 'lucide-react';

const getInitialFormData = (req) => {
  return {
    description: req?.description || '',
    shopID: req?.shopID || null,
    workCategoryID: req?.workCategoryID || null,
    urgencyID: req?.urgencyID || null,
    assignedContractorID: req?.assignedContractorID || null,
    status: req?.status || 'In work',
    customDays: req?.daysForTask || '',
  };
};

export default function RequestForm({ currentRequest, onSubmit, onCancel, apiError, shops, workCategories, urgencyCategories, contractors, isSubmitting  }) {
    const [formData, setFormData] = useState(() => getInitialFormData(currentRequest));
    const [chatInfo, setChatInfo] = useState({ status: 'idle', data: null });
    
    // --- Состояния для файлов ---
    const [files, setFiles] = useState([]);
    const [fileError, setFileError] = useState('');

    const isEditing = !!currentRequest;

    const selectedUrgency = urgencyCategories.find(u => u.urgencyID === formData.urgencyID);
    const isCustomizable = selectedUrgency?.urgencyName === 'Customizable';

    useEffect(() => {
        if (isCustomizable && formData.customDays === '') {
            setFormData(prev => ({ ...prev, customDays: selectedUrgency.defaultDays || 30 }));
        }
    }, [isCustomizable, selectedUrgency, formData.customDays]);

    useEffect(() => {
      const checkChat = async () => {
        if (formData.shopID && formData.assignedContractorID) {
            setChatInfo({ status: 'loading', data: null });
            try {
                const res = await getShopContractorChats({ size: 1000 });
                const chats = res.data.content;
                const shopId = formData.shopID;
                const contractorId = formData.assignedContractorID;

                let matchedChat = chats.find(c => c.shopID === shopId && c.contractorID === contractorId);
                if (!matchedChat) matchedChat = chats.find(c => c.shopID === null && c.contractorID === contractorId);
                if (!matchedChat) matchedChat = chats.find(c => c.shopID === shopId && c.contractorID === null);

                if (matchedChat) {
                    setChatInfo({ status: 'found', data: matchedChat });
                } else {
                    setChatInfo({ status: 'not_found', data: null });
                }
            } catch (error) {
                console.error("Ошибка проверки чата", error);
                setChatInfo({ status: 'idle', data: null });
            }
        } else {
            setChatInfo({ status: 'idle', data: null });
        }
      };
      checkChat();
    }, [formData.shopID, formData.assignedContractorID]);
    
    // --- Обработчики файлов ---
    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
        const MAX_SIZE = 5 * 1024 * 1024;

        if (files.length + selectedFiles.length > 10) {
            setFileError('Можно прикрепить не более 10 фотографий.');
            e.target.value = null;
            return;
        }

        const oversizedFile = selectedFiles.find(f => f.size > MAX_SIZE);
        if (oversizedFile) {
            setFileError(`Файл ${oversizedFile.name} слишком большой (макс. 5МБ).`);
            e.target.value = null;
            return;
        }

        setFileError('');
        setFiles(prev => [...prev, ...selectedFiles]);
        e.target.value = null; 
    };

    const removeFile = (indexToRemove) => {
        setFiles(prev => prev.filter((_, index) => index !== indexToRemove));
    };

    const handlePaste = useCallback((e) => {
        if (isEditing) return; // Разрешаем вставку только при создании
        const items = e.clipboardData.items;
        const newFiles = [];
        const MAX_SIZE = 5 * 1024 * 1024;

        for (let i = 0; i < items.length; i++) {
            if (items[i].type.indexOf("image") !== -1) {
                const file = items[i].getAsFile();
                if (file) {
                    if (file.size > MAX_SIZE) {
                        setFileError(`Вставленный файл слишком большой (макс. 5МБ).`);
                        continue;
                    }
                    newFiles.push(file);
                }
            }
        }

        if (newFiles.length > 0) {
            if (files.length + newFiles.length > 10) {
                setFileError('Можно прикрепить не более 10 фотографий.');
                return;
            }
            setFileError('');
            setFiles(prev => [...prev, ...newFiles]);
        }
    }, [isEditing, files.length]);

    useEffect(() => {
        window.addEventListener('paste', handlePaste);
        return () => window.removeEventListener('paste', handlePaste);
    }, [handlePaste]);

    // --- Обработчики формы ---
    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleSelectChange = (name, value) => {
        const finalValue = value === 'NONE' ? null : parseInt(value, 10);
        setFormData(prev => ({ ...prev, [name]: finalValue }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        const dataToSend = { ...formData };
        if (!isCustomizable) {
            delete dataToSend.customDays;
        }
        // Передаем файлы вместе с датой!
        onSubmit(dataToSend, files);
    };

    return (
        <form onSubmit={handleSubmit} className="flex flex-col h-full pt-4">
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-h-[60vh] overflow-y-auto px-1 pr-2 custom-scrollbar">
                
                {apiError && <p className="col-span-1 md:col-span-2 text-red-600 p-2 bg-red-50 rounded-md">{apiError}</p>}
                
                <div className="space-y-2">
                    <Label htmlFor="shopID">Магазин <span className="text-destructive">*</span></Label>
                    <Select onValueChange={(v) => handleSelectChange('shopID', v)} value={formData.shopID?.toString() || ''}>
                        <SelectTrigger><SelectValue placeholder="Выберите магазин..." /></SelectTrigger>
                        <SelectContent>
                            {shops.map(s => <SelectItem key={s.shopID} value={s.shopID.toString()}>{s.shopName}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                
                <div className="space-y-2">
                    <Label htmlFor="assignedContractorID">Исполнитель <span className="text-destructive">*</span></Label>
                    <Select onValueChange={(v) => handleSelectChange('assignedContractorID', v)} value={formData.assignedContractorID?.toString() || ''}>
                        <SelectTrigger><SelectValue placeholder="Выберите исполнителя..." /></SelectTrigger>
                        <SelectContent>
                            {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>

                <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="description">Описание <span className="text-destructive">*</span></Label>
                    <Textarea id="description" name="description" value={formData.description} onChange={handleChange} />
                </div>

                {/* --- СЕКЦИЯ ПРИКРЕПЛЕНИЯ ФОТО (ТОЛЬКО ПРИ СОЗДАНИИ) --- */}
                {!isEditing && (
                    <div className="space-y-2 md:col-span-2 p-4 bg-slate-50 border border-slate-200 rounded-lg">
                        <Label className="flex items-center gap-2 text-slate-700">
                            <Paperclip className="w-4 h-4" /> Прикрепить фото (макс. 10 шт)
                        </Label>
                        <div className="flex flex-col gap-3">
                            <Input 
                                type="file" 
                                multiple 
                                accept="image/*"
                                onChange={handleFileChange}
                                className="bg-white file:mr-4 file:py-1 file:px-2 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                            />
                            <p className="text-xs text-slate-500">Также можно вставить картинку из буфера обмена (Ctrl+V)</p>
                            
                            {files.length > 0 && (
                                <div className="flex flex-wrap gap-3 mt-2">
                                    {files.map((file, idx) => (
                                        <div key={idx} className="relative w-16 h-16 group">
                                            <img src={URL.createObjectURL(file)} className="w-full h-full object-cover rounded-md border border-slate-300 shadow-sm" alt="preview" />
                                            <button 
                                                type="button" 
                                                onClick={() => removeFile(idx)} 
                                                className="absolute -top-2 -right-2 bg-red-500 hover:bg-red-600 text-white rounded-full p-0.5 shadow transition-colors"
                                            >
                                                <X size={12} />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}
                            {fileError && <p className="text-red-500 text-xs font-medium">{fileError}</p>}
                        </div>
                    </div>
                )}

                <div className="space-y-2">
                    <Label htmlFor="workCategoryID">Вид работы <span className="text-destructive">*</span></Label>
                     <Select onValueChange={(v) => handleSelectChange('workCategoryID', v)} value={formData.workCategoryID?.toString() || ''}>
                         <SelectTrigger><SelectValue placeholder="Выберите вид работы..." /></SelectTrigger>
                        <SelectContent>
                            {workCategories.map(wc => <SelectItem key={wc.workCategoryID} value={wc.workCategoryID.toString()}>{wc.workCategoryName}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>

                <div className="space-y-2">
                    <Label htmlFor="urgencyID">Срочность <span className="text-destructive">*</span></Label>
                     <Select onValueChange={(v) => handleSelectChange('urgencyID', v)} value={formData.urgencyID?.toString() || ''}>
                        <SelectTrigger><SelectValue placeholder="Выберите срочность..." /></SelectTrigger>
                        <SelectContent>
                            {urgencyCategories.map(uc => <SelectItem key={uc.urgencyID} value={uc.urgencyID.toString()}>{getUrgencyDisplayName(uc.urgencyName)}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>
                
                {isCustomizable && (
                    <div className="space-y-2 md:col-span-2">
                        <Label htmlFor="customDays">Дней на выполнение (настраиваемая) <span className="text-destructive">*</span></Label>
                        <Input id="customDays" name="customDays" type="number" min="1" max="365" value={formData.customDays} onChange={handleChange} required />
                    </div>
                )}

                {isEditing && (
                    <div className="space-y-2">
                        <Label htmlFor="status">Статус</Label>
                         <Select onValueChange={(v) => setFormData(p => ({...p, status: v}))} value={formData.status}>
                            <SelectTrigger><SelectValue /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value="In work">В работе</SelectItem>
                                <SelectItem value="Done">Выполнена</SelectItem>
                                <SelectItem value="Closed">Закрыта</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>
                )}
                
                {chatInfo.status === 'not_found' && (
                    <div className="md:col-span-2 flex items-start gap-2 text-orange-700 text-sm p-3 bg-orange-50 rounded-md border border-orange-200">
                        <AlertTriangle className="h-5 w-5 shrink-0" />
                        <div>
                            <p className="font-semibold">Внимание: Чат не найден!</p>
                            <p>Для этой пары "Магазин-Исполнитель" не настроен Telegram-чат. Уведомления по этой заявке отправляться не будут.</p>
                        </div>
                    </div>
                )}

                {chatInfo.status === 'found' && (
                    <div className="md:col-span-2 flex items-start gap-2 text-emerald-800 text-sm p-3 bg-emerald-50 rounded-md border border-emerald-200">
                        <Send className="h-5 w-5 shrink-0 mt-0.5 text-emerald-600" />
                        <div>
                            <p className="font-semibold mb-1">Уведомления по заявке будут отправляться в чат:</p>
                            <p className="text-emerald-700">
                                (Применено правило: {chatInfo.data.shopName ? `Магазин: ${chatInfo.data.shopName}` : 'Все магазины'} + {chatInfo.data.contractorLogin ? `Подрядчик: ${chatInfo.data.contractorLogin}` : 'Все подрядчики'})<br/>
                                ID чата: <b className="font-mono text-xs bg-emerald-100 px-1 rounded">{chatInfo.data.telegramID}</b>
                            </p>
                        </div>
                    </div>
                )}
            </div>

            <div className="flex justify-end gap-2 pt-4 mt-4 border-t">
                <Button type="button" variant="outline" onClick={onCancel} disabled={isSubmitting}>
                    Отмена
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? (
                        <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            Сохранение...
                        </>
                    ) : (
                        isEditing ? 'Сохранить' : 'Создать'
                    )}
                </Button>
            </div>
        </form>
    );
}