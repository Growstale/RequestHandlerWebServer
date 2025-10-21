import React from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { getUrgencyDisplayName, getStatusDisplayName } from '@/lib/displayNames';
import { cn } from '@/lib/utils';

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

const renderDeadlineInfo = (request) => {
    if (request.status === 'Closed' || request.daysRemaining === null) {
        return '—';
    }
    if (request.daysRemaining >= 0) {
        return `Осталось ${request.daysRemaining} дн.`;
    }
    return `Просрочено на ${Math.abs(request.daysRemaining)} дн.`;
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
                        {request.urgencyName === 'Customizable' && request.daysForTask && (
                             <div>
                                <p className="font-semibold text-gray-700">Дней на выполнение:</p>
                                <p>{request.daysForTask}</p>
                            </div>
                        )}
                    </div>

                    <div className="space-y-4">
                        <div>
                            <p className="font-semibold text-gray-700">Статус:</p>
                            <p>{getStatusDisplayName(request.status)}</p>
                        </div>
                         <div>
                            <p className="font-semibold text-gray-700">Срок:</p>
                            <p className={cn({
                                'font-bold text-red-600': request.daysRemaining < 0,
                                'text-gray-700': request.daysRemaining > 0
                            })}>
                                {renderDeadlineInfo(request)}
                            </p>
                        </div>
                        <div>
                            <p className="font-semibold text-gray-700">Исполнитель:</p>
                            <p>{request.assignedContractorName || 'Не назначен'}</p>
                        </div>
                         <div>
                            <p className="font-semibold text-gray-700">Дата создания:</p>
                            <p>{formatDate(request.createdAt)}</p>
                        </div>
                        {request.status === 'Closed' && request.closedAt && (
                             <div>
                                <p className="font-semibold text-gray-700">Дата закрытия:</p>
                                <p>{formatDate(request.closedAt)}</p>
                            </div>
                        )}
                    </div>
                    
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