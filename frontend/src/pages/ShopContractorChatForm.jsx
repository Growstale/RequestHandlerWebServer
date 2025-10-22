import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const getInitialFormData = (chat) => ({
  shopID: chat?.shopID || null,
  contractorID: chat?.contractorID || null,
  telegramID: chat?.telegramID || '',
});

export default function ShopContractorChatForm({ currentChat, shops, contractors, onSubmit, onCancel, apiError }) {
    const [formData, setFormData] = useState(() => getInitialFormData(currentChat));
    const isEditing = !!currentChat;

    const handleSelectChange = (name, value) => {
        setFormData(prev => ({ ...prev, [name]: value ? parseInt(value, 10) : null }));
    };

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        onSubmit(formData);
    };

    return (
        <form onSubmit={handleSubmit} className="space-y-4 pt-4">
            {apiError && <p className="text-red-600 text-sm p-2 bg-red-50 rounded-md">{apiError}</p>}
            
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
                <Label htmlFor="contractorID">Подрядчик <span className="text-destructive">*</span></Label>
                <Select onValueChange={(v) => handleSelectChange('contractorID', v)} value={formData.contractorID?.toString() || ''}>
                    <SelectTrigger><SelectValue placeholder="Выберите подрядчика..." /></SelectTrigger>
                    <SelectContent>
                        {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                    </SelectContent>
                </Select>
            </div>
            
            <div className="space-y-2">
                <Label htmlFor="telegramID">Telegram ID чата <span className="text-destructive">*</span></Label>
                <Input
                    id="telegramID"
                    name="telegramID"
                    type="number"
                    value={formData.telegramID}
                    onChange={handleChange}
                    placeholder="Например, -100123456789"
                    required
                />
            </div>
            
            <div className="flex justify-end gap-2 pt-4">
                <Button type="button" variant="outline" onClick={onCancel}>Отмена</Button>
                <Button type="submit">{isEditing ? 'Сохранить' : 'Создать'}</Button>
            </div>
        </form>
    );
}