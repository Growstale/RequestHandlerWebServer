import React from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { getUrgencyDisplayName } from '@/lib/displayNames';

// Функция для форматирования даты
const formatDate = (dateString) => {
    if (!dateString) return '—';
    return new Date(dateString).toLocaleString('ru-RU', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
};

export default function RequestDetailsModal({ isOpen, onClose, request }) {
    if (!request) return null;

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Детали заявки #{request.requestID}</DialogTitle>
                </DialogHeader>
                <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4 text-sm">
                    {/* Левая колонка */}
                    <div className="space-y-4">
                        <div>
                            <p className="font-semibold text-gray-700">Магазин:</p>
                            <p>{request.shopName}</p>
                        </div>
                        <div>
                            <p className="font-semibold text-gray-700">Вид работы:</p>
                            <p>{request.workCategoryName}</p>
                        </div>
                        <div>
                            <p className="font-semibold text-gray-700">Срочность:</p>
                            <p>{getUrgencyDisplayName(request.urgencyName)}</p>
                        </div>
                    </div>

                    {/* Правая колонка */}
                    <div className="space-y-4">
                        <div>
                            <p className="font-semibold text-gray-700">Статус:</p>
                            <p>{request.status === 'In work' ? 'В работе' : request.status === 'Done' ? 'Выполнена' : 'Закрыта'}</p>
                        </div>
                        <div>
                            <p className="font-semibold text-gray-700">Исполнитель:</p>
                            <p>{request.assignedContractorName || 'Не назначен'}</p>
                        </div>
                        <div>
                            <p className="font-semibold text-gray-700">Дата создания:</p>
                            <p>{formatDate(request.createdAt)}</p>
                        </div>
                    </div>
                    
                    {/* Описание на всю ширину */}
                    <div className="md:col-span-2 pt-4 border-t">
                        <p className="font-semibold text-gray-700">Описание:</p>
                        <p className="mt-1 whitespace-pre-wrap bg-gray-50 p-3 rounded-md">
                            {request.description || 'Описание отсутствует.'}
                        </p>
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}