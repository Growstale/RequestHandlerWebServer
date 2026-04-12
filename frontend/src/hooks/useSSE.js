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
                console.warn('❌ SSE: Ошибка соединения. Пытаемся переподключиться...');
                
                globalSSE.close();
                globalSSE = null;

                api.get('/api/user/whoami').catch(err => {
                    console.log('SSE: whoami failed, waiting for axios interceptor to resolve it.');
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