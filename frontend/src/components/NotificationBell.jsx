import React, { useState, useEffect } from 'react';
import { Bell, Trash2 } from 'lucide-react';
import api from '@/api/axios';
import { useNavigate } from 'react-router-dom';
import { cn } from '@/lib/utils';

export default function NotificationBell() {
    const [notifications, setNotifications] = useState([]);
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();

    const fetchNotifications = async () => {
        try {
            const res = await api.get('/api/web-notifications');
            console.log("Полученные уведомления:", res.data);
            setNotifications(res.data);
        } catch (e) { console.error(e); }
    };

    useEffect(() => {
        fetchNotifications();
        const timer = setInterval(fetchNotifications, 30000);
        return () => clearInterval(timer);
    }, []);

    const handleAction = async (id, requestId) => {
        await api.delete(`/api/web-notifications/${id}`);
        setNotifications(prev => prev.filter(n => n.notificationID !== id));
        
        if (requestId) {
            navigate(`/requests?openId=${requestId}`);
        }
        setOpen(false);
    };

    const clearAll = async () => {
        await api.delete('/api/web-notifications/clear-all');
        setNotifications([]);
    };

    return (
        <div className="relative">
            {/* Кнопка колокольчика */}
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

            {/* Выпадающее окно */}
            {open && (
                <>
                    {/* Прозрачная подложка для закрытия при клике вне окна */}
                    <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
                    
                    <div className="absolute bottom-full left-0 mb-2 w-80 bg-white border rounded-lg shadow-2xl z-50 overflow-hidden flex flex-col animate-in fade-in slide-in-from-bottom-2 duration-200">
                        {/* Заголовок */}
                        <div className="p-3 border-b flex justify-between items-center bg-gray-50 shrink-0">
                            <span className="font-bold text-sm text-gray-700">Уведомления</span>
                            {notifications.length > 0 && (
                                <button 
                                    onClick={clearAll} 
                                    className="text-xs text-red-500 hover:text-red-700 font-medium flex items-center gap-1 transition-colors"
                                >
                                    <Trash2 size={12}/> Очистить всё
                                </button>
                            )}
                        </div>

                        {/* Список */}
                        <div className="max-h-80 overflow-y-auto custom-scrollbar">
                            {notifications.length === 0 ? (
                                <div className="p-8 text-center text-gray-400 text-sm italic">
                                    Нет новых уведомлений
                                </div>
                            ) : (
                                notifications.map(n => (
                                    <div 
                                        key={n.notificationID} 
                                        onClick={() => handleAction(n.notificationID, n.requestID)}
                                        className="p-3 border-b last:border-b-0 hover:bg-blue-50 cursor-pointer transition-colors group"
                                    >
                                        <div className="text-sm font-semibold text-blue-700 group-hover:text-blue-800 transition-colors">
                                            {n.title}
                                        </div>
                                        <div className="text-xs text-gray-600 line-clamp-2 mt-0.5">
                                            {n.message}
                                        </div>
                                        <div className="text-[10px] text-gray-400 mt-1.5 flex justify-end">
                                            {new Date(n.createdAt).toLocaleString('ru-RU', {
                                                hour: '2-digit',
                                                minute: '2-digit',
                                                day: '2-digit',
                                                month: '2-digit'
                                            })}
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