import { useEffect, useRef } from 'react';
import { useAuth } from '@/context/AuthProvider';
import api from '@/api/axios';

let globalSSE = null;
const subscribers = new Set();

export function useSSE(onMessage) {
    const { accessToken, logout } = useAuth(); // Добавили logout из AuthProvider
    const onMessageRef = useRef(onMessage);

    // Обновляем реф, чтобы всегда иметь актуальный обработчик
    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    useEffect(() => {
        if (!accessToken) return;

        subscribers.add(onMessageRef.current);

        const connect = () => {
            // Если соединение уже открыто - не дублируем
            if (globalSSE && (globalSSE.readyState === EventSource.CONNECTING || globalSSE.readyState === EventSource.OPEN)) {
                return;
            }

            globalSSE = new EventSource(`/api/updates/stream?token=${accessToken}`, { withCredentials: true });

            globalSSE.onopen = () => console.log('✅ SSE: Соединение установлено');

            globalSSE.onmessage = (event) => {
                if (event.data === 'ping') return;
                subscribers.forEach(callback => callback(event.data));
            };

            globalSSE.onerror = (e) => {
                // Если статус 401 - значит токен протух
                // EventSource сам по себе не возвращает статус код ошибки, 
                // но если соединение закрывается и мы не можем подключиться - считаем что токен умер
                console.warn('❌ SSE: Ошибка соединения (возможно протух токен)');
                
                globalSSE.close();
                globalSSE = null;

                api.get('/api/user/whoami').catch(err => {
                    if (err.response?.status === 401) {
                        console.log('SSE: Токен истек, вызываем logout...');
                        logout(); // Очистит accessToken в AuthProvider, вызовет перерендер
                    }
                });
            };
        };

        connect();

        return () => {
            subscribers.delete(onMessageRef.current);
            if (subscribers.size === 0 && globalSSE) {
                globalSSE.close();
                globalSSE = null;
                console.log('🛑 SSE: Отключено');
            }
        };
    }, [accessToken, logout]); 
}