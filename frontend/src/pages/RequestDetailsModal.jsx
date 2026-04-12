import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog"; 
import { getUrgencyDisplayName, getStatusDisplayName } from '@/lib/displayNames';
import { getShopContractorChats } from '@/api/shopContractorChatApi';
import { cn } from '@/lib/utils';
import { Send, AlertTriangle, Loader2, RotateCcw } from 'lucide-react';

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
    if (request.urgencyName === 'Notes' || request.status === 'Closed' || request.daysRemaining === null) {
        return '—';
    }
    if (request.daysRemaining >= 0) {
        return `Осталось ${request.daysRemaining} дн.`;
    }
    return `Просрочено на ${Math.abs(request.daysRemaining)} дн.`;
};


export default function RequestDetailsModal({ isOpen, onClose, request, footerContent, highlightUpdate, isAdmin }) {
    const [chatInfo, setChatInfo] = useState({ status: 'idle', data: null });

    // Загрузка информации о чате при открытии модалки
    useEffect(() => {
        if (isOpen && request?.shopID && request?.assignedContractorID) {
            setChatInfo({ status: 'loading', data: null });
            
            getShopContractorChats({ size: 1000 }).then(res => {
                const chats = res.data.content;
                const shopId = request.shopID;
                const contractorId = request.assignedContractorID;

                // Каскадный поиск чата
                let matchedChat = chats.find(c => c.shopID === shopId && c.contractorID === contractorId);
                if (!matchedChat) matchedChat = chats.find(c => c.shopID === null && c.contractorID === contractorId);
                if (!matchedChat) matchedChat = chats.find(c => c.shopID === shopId && c.contractorID === null);

                if (matchedChat) {
                    setChatInfo({ status: 'found', data: matchedChat });
                } else {
                    setChatInfo({ status: 'not_found', data: null });
                }
            }).catch(err => {
                console.error("Ошибка загрузки чатов", err);
                setChatInfo({ status: 'idle', data: null });
            });
        }
    }, [isOpen, request]);

    if (!request) return null;

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-2xl w-[95vw] sm:w-full max-h-[95vh] md:max-h-[90vh] flex flex-col p-0 rounded-xl overflow-hidden">
                
                <DialogHeader className="p-6 pb-2 shrink-0">
                    <DialogTitle>Детали заявки #{request.requestID}</DialogTitle>
                </DialogHeader>

                <div className="flex-1 overflow-y-auto p-6 pt-0 custom-scrollbar text-sm">
                    {/* КРАСИВЫЕ ПЛАШКИ УВЕДОМЛЕНИЙ */}
                    {highlightUpdate?.overdue && (
                        <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-800 rounded-md font-semibold animate-in fade-in zoom-in duration-500 flex items-center gap-2">
                            <AlertTriangle className="h-5 w-5" />
                            Внимание: Заявка была просрочена!
                        </div>
                    )}
                    {highlightUpdate?.restored && (
                        <div className="mb-4 p-3 bg-indigo-50 border border-indigo-200 text-indigo-800 rounded-md font-semibold animate-in fade-in zoom-in duration-500 flex items-center gap-2">
                            <RotateCcw className="h-5 w-5" />
                            Заявка была восстановлена из архива.
                        </div>
                    )}

<div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 gap-y-4">
                        <div className="space-y-4">
                            <div className={cn("p-1 rounded transition-colors duration-1000", highlightUpdate?.shop && "bg-yellow-100 ring-1 ring-yellow-300 shadow-sm")}>
                                <p className="font-semibold text-gray-700">Магазин:</p>
                                <p>{request.shopName}</p>
                            </div>
                            <div className="p-1">
                                <p className="font-semibold text-gray-700">Вид работы:</p>
                                <p>{request.workCategoryName}</p>
                            </div>
                            <div className={cn("p-1 rounded transition-colors duration-1000", highlightUpdate?.urgency && "bg-yellow-100 ring-1 ring-yellow-300 shadow-sm")}>
                                <p className="font-semibold text-gray-700">Срочность:</p>
                                <p>{getUrgencyDisplayName(request.urgencyName)}</p>
                            </div>
                            
                            <div className="p-1">
                                <p className="font-semibold text-gray-700">Создана:</p>
                                <p>{formatDate(request.createdAt)}</p>
                            </div>
                        </div>

                        <div className="space-y-4">
                            <div className={cn("p-1 rounded transition-colors duration-1000", highlightUpdate?.status && "bg-yellow-100 ring-1 ring-yellow-300 shadow-sm")}>
                                <p className="font-semibold text-gray-700">Статус:</p>
                                <p>{getStatusDisplayName(request.status)}</p>
                            </div>
                            <div>
                                <p className="font-semibold text-gray-700">Срок:</p>
                                <p className={cn(request.isOverdue && request.status === 'In work' && 'font-bold text-red-600')}>
                                    {renderDeadlineInfo(request)}
                                </p>
                            </div>
                            <div className={cn("p-1 rounded transition-colors duration-1000", highlightUpdate?.contractor && "bg-yellow-100 ring-1 ring-yellow-300 shadow-sm")}>
                                <p className="font-semibold text-gray-700">Исполнитель:</p>
                                <p>{request.assignedContractorName || 'Не назначен'}</p>
                            </div>

                            {request.status === 'Closed' && request.closedAt && (
                                <div className="p-1">
                                    <p className="font-semibold text-gray-700">Закрыта:</p>
                                    <p>{formatDate(request.closedAt)}</p>
                                </div>
                            )}
                        </div>

                        <div className="md:col-span-2 pt-4 border-t">
                            <p className="font-semibold text-gray-700">Описание:</p>
                            <p className={cn("mt-1 whitespace-pre-wrap p-3 rounded-md transition-colors duration-1000", 
                                highlightUpdate?.description ? "bg-yellow-100 border border-yellow-300 shadow-sm" : "bg-gray-50")}>
                                {request.description || 'Описание отсутствует.'}
                            </p>
                        </div>

                        {isAdmin && (
                        <div className="md:col-span-2 pt-2">
                            <p className="font-semibold text-gray-700 mb-2">Уведомления в Telegram:</p>
                            
                            {chatInfo.status === 'loading' && (
                                <div className="flex items-center gap-2 text-gray-500 text-xs">
                                    <Loader2 className="h-3 w-3 animate-spin" />
                                    <span>Проверка маршрутизации...</span>
                                </div>
                            )}

                            {chatInfo.status === 'not_found' && (
                                <div className="flex items-start gap-2 text-orange-700 bg-orange-50 p-2.5 rounded border border-orange-200 text-xs">
                                    <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
                                    <p>Чат для данной заявки не настроен. Бот не отправляет сообщения об изменениях по ней.</p>
                                </div>
                            )}

                            {chatInfo.status === 'found' && (
                                <div className="flex items-start gap-2 text-blue-800 bg-blue-50 p-2.5 rounded border border-blue-200 text-xs">
                                    <Send className="h-4 w-4 shrink-0 mt-0.5 text-blue-600" />
                                    <div>
                                        <p>Все события по заявке направляются в чат: <b className="font-mono bg-blue-100 px-1 rounded">{chatInfo.data.telegramID}</b></p>
                                        <p className="text-[11px] text-blue-600 mt-1">
                                            ({chatInfo.data.shopName ? `Магазин: ${chatInfo.data.shopName}` : 'Все магазины'} + {chatInfo.data.contractorLogin ? `Подрядчик: ${chatInfo.data.contractorLogin}` : 'Все подрядчики'})
                                        </p>
                                    </div>
                                </div>
                            )}
                        </div>
                        )}
                    </div>
                </div>

                {footerContent && (
                    <DialogFooter className="p-6 pt-4 border-t shrink-0">
                        {footerContent}
                    </DialogFooter>
                )}
            </DialogContent>
        </Dialog>
    );
}