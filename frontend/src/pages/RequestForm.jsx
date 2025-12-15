import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { getUrgencyDisplayName } from '@/lib/displayNames';
import { checkShopContractorChatExists } from '@/api/shopContractorChatApi';
import { Loader2 } from 'lucide-react';

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
    const [chatWarning, setChatWarning] = useState(null);
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
        setChatWarning(null);
        if (formData.shopID && formData.assignedContractorID) {
          try {
            const res = await checkShopContractorChatExists(formData.shopID, formData.assignedContractorID);
            if (!res.data) {
              setChatWarning('Внимание: Для этой пары "Магазин-Исполнитель" не настроен Telegram-чат. Уведомления отправляться не будут.');
            }
          } catch (error) {
            console.error("Ошибка проверки чата", error);
          }
        }
      };
      checkChat();
    }, [formData.shopID, formData.assignedContractorID]);
    
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
        onSubmit(dataToSend);
    };

    return (
        // ИЗМЕНЕНИЕ 1: Убрали grid из самого тега form, сделали flex column
        <form onSubmit={handleSubmit} className="flex flex-col h-full pt-4">
            
            {/* ИЗМЕНЕНИЕ 2: Обернули поля в div с ограничением высоты (max-h) и скроллом (overflow-y-auto) */}
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
                    {/* Textarea теперь внутри скроллируемого контейнера */}
                    <Textarea id="description" name="description" value={formData.description} onChange={handleChange} />
                </div>

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
                
                {chatWarning && (
                  <p className="md:col-span-2 text-orange-600 text-sm p-3 bg-orange-50 rounded-md border border-orange-200">{chatWarning}</p>
                )}
            </div>

            {/* ИЗМЕНЕНИЕ 3: Кнопки вынесены в отдельный блок футера с border-t (линией сверху) */}
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