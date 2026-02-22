import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Bell, Trash2 } from 'lucide-react';
import api from '@/api/axios';
import { useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthProvider';

export default function NotificationBell() {
    const [notifications, setNotifications] = useState([]);
    const [open, setOpen] = useState(false);
    const { user, accessToken } = useAuth();
    const navigate = useNavigate();
    
    // Используем useRef для хранения EventSource, чтобы иметь к нему доступ вне useEffect
    const sseRef = useRef(null);
    const bellRef = useRef(null);

    const fetchNotifications = useCallback(async () => {
        // Если нет токена, даже не пытаемся запрашивать список
        if (!accessToken) return;
        try {
            const res = await api.get('/api/web-notifications');
            setNotifications(res.data);
        } catch (e) {
            console.error("Ошибка получения уведомлений:", e);
        }
    }, [accessToken]);

    useEffect(() => {
        if (accessToken && user?.id) {
            fetchNotifications();

            // Закрываем предыдущее соединение, если оно было
            if (sseRef.current) {
                sseRef.current.close();
            }

            const url = `/api/updates/stream?token=${accessToken}`;
            const eventSource = new EventSource(url);
            sseRef.current = eventSource;

            eventSource.onopen = () => console.log("SSE подключено (Колокольчик)");

            eventSource.onmessage = (event) => {
                if (event.data === `WEB_NOTIFICATION_USER_${user.id}`) {
                    fetchNotifications();
                }
            };

            eventSource.onerror = (err) => {
                if (eventSource.readyState !== EventSource.CONNECTING) {
                    console.error("SSE Error:", err);
                }
            };

            return () => {
                eventSource.close();
                sseRef.current = null;
            };
        }
    }, [accessToken, user?.id, fetchNotifications]);

    useEffect(() => {
        const handleClickOutside = (event) => {
            // Если окно открыто И клик был НЕ по нашему компоненту (bellRef)
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
        try {
            await api.delete(`/api/web-notifications/${id}`);
            setNotifications(prev => prev.filter(n => n.notificationID !== id));
            if (requestId) {
                navigate(`/requests?openId=${requestId}`);
            }
            setOpen(false);
        } catch (e) {
            console.error(e);
        }
    };

    const clearAll = async () => {
        try {
            await api.delete('/api/web-notifications/clear-all');
            setNotifications([]);
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
                    <span className="absolute top-1 right-1 bg-red-500 text-white text-[10px] font-bold rounded-full h-4 w-4 flex items-center justify-center border-2 border-white">
                        {notifications.length}
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