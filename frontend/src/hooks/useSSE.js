import { useEffect, useRef } from 'react';
import { useAuth } from '@/context/AuthProvider';
import api from '@/api/axios';

let globalSSE = null;
const subscribers = new Set();
let disconnectTimeout = null;

export function useSSE(onMessage) {
    const { accessToken, logout } = useAuth();
    const onMessageRef = useRef(onMessage);

    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    useEffect(() => {
        if (!accessToken) return;

        // Сохраняем сам объект ref, а не функцию. 
        // Это навсегда решит проблему "утечки" старых коллбэков при перерисовках.
        subscribers.add(onMessageRef);

        const connect = () => {
            if (globalSSE && (globalSSE.readyState === EventSource.CONNECTING || globalSSE.readyState === EventSource.OPEN)) {
                if (disconnectTimeout) {
                    clearTimeout(disconnectTimeout);
                    disconnectTimeout = null;
                }
                return;
            }

            if (disconnectTimeout) {
                clearTimeout(disconnectTimeout);
                disconnectTimeout = null;
            }

            globalSSE = new EventSource(`/api/updates/stream?token=${accessToken}`, { withCredentials: true });

            globalSSE.onopen = () => console.log('✅ SSE: Соединение установлено');

            globalSSE.onmessage = (event) => {
                if (event.data === 'ping') return;
                
                const msg = event.data.trim();
                console.log('📥 SSE Event:', msg); // Логируем для отладки
                
                // Вызываем актуальную функцию из ref
                subscribers.forEach(ref => {
                    if (ref.current) {
                        ref.current(msg);
                    }
                });
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
            // Удаляем именно сам ref
            subscribers.delete(onMessageRef);
            if (subscribers.size === 0 && globalSSE) {
                disconnectTimeout = setTimeout(() => {
                    if (subscribers.size === 0 && globalSSE) {
                        globalSSE.close();
                        globalSSE = null;
                        console.log('🛑 SSE: Отключено');
                    }
                }, 1000);
            }
        };
    }, [accessToken, logout]); 
}