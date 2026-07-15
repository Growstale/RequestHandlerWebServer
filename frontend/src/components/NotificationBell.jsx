import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { Bell, Trash2 } from 'lucide-react';
import api from '@/api/axios';
import { useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthProvider';
import { useSSE } from '@/hooks/useSSE'; 

export default function NotificationBell() {
    const [notifications, setNotifications] = useState([]);
    const [open, setOpen] = useState(false);
    const { user, accessToken } = useAuth();
    const navigate = useNavigate();
    
    const sseRef = useRef(null);
    const bellRef = useRef(null);

    const fetchNotifications = useCallback(async () => {
        if (!accessToken) return;
        try {
            // Добавляем timestamp, чтобы браузер не кэшировал пустой ответ
            const res = await api.get(`/api/web-notifications?t=${new Date().getTime()}`);
            setNotifications(res.data);
        } catch (e) {
            console.error("Ошибка получения уведомлений:", e);
        }
    }, [accessToken]);

    useEffect(() => {
        fetchNotifications();
    }, [fetchNotifications]);

    // Слушаем кастомное событие для моментального обновления UI при удалении
    useEffect(() => {
        const handleRefresh = () => fetchNotifications();
        window.addEventListener('refresh-notifications', handleRefresh);
        return () => window.removeEventListener('refresh-notifications', handleRefresh);
    }, [fetchNotifications]);

    useSSE(useCallback((message) => {
        if (message === `WEB_NOTIFICATION_USER_${user?.id}`) {
            setTimeout(() => fetchNotifications(), 500);
        }
    }, [user?.id, fetchNotifications]));



    useEffect(() => {
        const handleClickOutside = (event) => {
            if (open && bellRef.current && !bellRef.current.contains(event.target)) {
                setOpen(false);
            }
        };

        // Вешаем слушатель на весь документ
        document.addEventListener('mousedown', handleClickOutside);
        
        // Убираем слушатель при размонтировании
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [open]);

    const handleAction = async (id, requestId) => {
        // Удаляем уведомление, не блокируя дальнейшие действия
        try {
            await api.delete(`/api/web-notifications/${id}`);
            setNotifications(prev => prev.filter(n => n.notificationID !== id));
            window.dispatchEvent(new Event('refresh-notifications')); // синхронизируем с каталогом
        } catch (e) {
            console.error("Не удалось удалить уведомление:", e);
        }

        // Переходим к заявке
    try {
            if (requestId) {
                const res = await api.get(`/api/requests/${requestId}`);
                const isClosed = res.data.status === 'Closed';
                
                const targetPath = isClosed ? '/requests/archive' : '/requests';
                navigate(`${targetPath}?openId=${requestId}`);
            }
        } catch (e) {
            console.error("Ошибка при получении статуса заявки:", e);
            // Если не удалось определить статус, всё равно пробуем перейти в активные заявки
            if (requestId) {
                navigate(`/requests?openId=${requestId}`);
            }
        } finally {
            setOpen(false);
        }
    };

    const clearAll = async () => {
        try {
            await api.delete('/api/web-notifications/clear-all');
            setNotifications([]);
            window.dispatchEvent(new Event('refresh-notifications')); // синхронизируем с каталогом
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <div className="relative" ref={bellRef}>
            <button 
                onClick={() => setOpen(!open)} 
                className={cn(
                    "p-2 rounded-full transition-colors relative hover:bg-gray-100",
                    open && "bg-gray-100"
                )}
            >
                <Bell className="h-6 w-6 text-gray-600" />
                {notifications.length > 0 && (
                    <span className="absolute top-0.5 right-0.5 translate-x-1 -translate-y-1 bg-red-500 text-white text-[10px] font-bold rounded-full min-w-[18px] h-[18px] px-1 flex items-center justify-center border-2 border-white shadow-sm">
                        {notifications.length > 9 ? '9+' : notifications.length}
                    </span>
                )}
            </button>

            {open && (
                <>
                    <div className="absolute bottom-full left-0 mb-2 w-80 bg-white border rounded-lg shadow-2xl z-50 overflow-hidden flex flex-col animate-in fade-in slide-in-from-bottom-2 duration-200">
                        <div className="p-3 border-b flex justify-between items-center bg-gray-50 shrink-0">
                            <span className="font-bold text-sm text-gray-700">Уведомления</span>
                            {notifications.length > 0 && (
                                <button onClick={clearAll} className="text-xs text-red-500 hover:text-red-700 font-medium flex items-center gap-1">
                                    <Trash2 size={12}/> Очистить всё
                                </button>
                            )}
                        </div>
                        <div className="max-h-80 overflow-y-auto custom-scrollbar">
                            {notifications.length === 0 ? (
                                <div className="p-8 text-center text-gray-400 text-sm italic">Нет новых уведомлений</div>
                            ) : (
                                notifications.map(n => (
                                    <div key={n.notificationID} onClick={() => handleAction(n.notificationID, n.requestID)} className="p-3 border-b last:border-b-0 hover:bg-blue-50 cursor-pointer transition-colors group">
                                        <div className="text-sm font-semibold text-blue-700">{n.title}</div>
                                        <div className="text-xs text-gray-600 line-clamp-2 mt-0.5">{n.message}</div>
                                        <div className="text-[10px] text-gray-400 mt-1.5 flex justify-end">
                                            {new Date(n.createdAt).toLocaleString('ru-RU')}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}