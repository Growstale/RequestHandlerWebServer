import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { getUrgencyDisplayName } from '@/lib/displayNames';

const getInitialFormData = (req) => {
  return {
    title: req?.title || '',
    description: req?.description || '',
    shopID: req?.shopID || null,
    workCategoryID: req?.workCategoryID || null,
    urgencyID: req?.urgencyID || null,
    assignedContractorID: req?.assignedContractorID || null,
    status: req?.status || 'In work',
    customDays: req?.customDays || '',
  };
};


export default function RequestForm({ currentRequest, onSubmit, onCancel, apiError, shops, workCategories, urgencyCategories, contractors }) {
    const [formData, setFormData] = useState(() => getInitialFormData(currentRequest));
    const isEditing = !!currentRequest;

    const selectedUrgency = urgencyCategories.find(u => u.urgencyID === formData.urgencyID);
    const isCustomizable = selectedUrgency?.urgencyName === 'Customizable';

    useEffect(() => {
        if (isCustomizable && formData.customDays === '') {
            setFormData(prev => ({ ...prev, customDays: selectedUrgency.defaultDays || 30 }));
        }
    }, [isCustomizable, selectedUrgency, formData.customDays]);
    
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
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4">
            {apiError && <p className="col-span-2 text-red-600 p-2 bg-red-50 rounded-md">{apiError}</p>}
            
            <div className="space-y-2">
                <Label htmlFor="shopID">Магазин <span className="text-destructive">*</span></Label>
                <Select onValueChange={(v) => handleSelectChange('shopID', v)} value={formData.shopID?.toString() || ''}>
                    <SelectTrigger><SelectValue placeholder="Выберите магазин..." /></SelectTrigger>
                    <SelectContent>
                        {shops.map(s => <SelectItem key={s.shopID} value={s.shopID.toString()}>{s.shopName}</SelectItem>)}
                    </SelectContent>
                </Select>
            </div>

            <div className="space-y-2 md:col-span-2">
                <Label htmlFor="description">Описание <span className="text-destructive">*</span></Label>
                <Textarea id="description" name="description" value={formData.description} onChange={handleChange} required />
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
                <div className="space-y-2">
                    <Label htmlFor="customDays">Дней на выполнение (настраиваемая) <span className="text-destructive">*</span></Label>
                    <Input id="customDays" name="customDays" type="number" min="1" max="365" value={formData.customDays} onChange={handleChange} required />
                </div>
            )}

            <div className="space-y-2">
                <Label htmlFor="assignedContractorID">Диспетчер</Label>
                <Select onValueChange={(v) => handleSelectChange('assignedContractorID', v)} value={formData.assignedContractorID?.toString() || 'NONE'}>
                    <SelectTrigger><SelectValue placeholder="Не назначен" /></SelectTrigger>
                    <SelectContent>
                        <SelectItem value="NONE">Не назначен</SelectItem>
                        {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                    </SelectContent>
                </Select>
            </div>

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
            
            <div className="md:col-span-2 flex justify-end gap-2 pt-4">
                <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
                <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
            </div>
        </form>
    );
}