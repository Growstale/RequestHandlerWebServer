// src/pages/GanttChartView.jsx

import React, { useState, useEffect, useRef } from 'react';
import { gantt } from 'dhtmlx-gantt'; // <-- ПРАВИЛЬНЫЙ ИМПОРТ
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'; // <-- ПРАВИЛЬНЫЙ ИМПОРТ СТИЛЕЙ
import { getRequests } from '@/api/requestApi';
import { RefreshCw, AlertTriangle } from 'lucide-react';

export default function GanttChartView({ filters, onTaskClick }) {
    const [data, setData] = useState({ data: [], links: [] });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const ganttContainer = useRef(null);

    useEffect(() => {
        // --- Логика загрузки данных ---
        const fetchAllRequests = async () => {
            setLoading(true);
            setError(null);
            setData({ data: [], links: [] });
            try {
                const response = await getRequests({ ...filters, page: 0, size: 1000 });
                
                const tasks = response.data.content
                    .filter(req => req.daysForTask > 0)
                    .map(req => {
                        let color = '#22c55e'; // Зеленый
                        if (req.isOverdue && req.status === 'In work') {
                            color = '#ef4444'; // Красный
                        } else if (req.status === 'Done') {
                            color = '#3b82f6'; // Синий
                        }

                        return {
                            id: req.requestID,
                            text: `#${req.requestID} ${req.shopName}`,
                            start_date: new Date(req.createdAt), // Используем объект Date
                            duration: req.daysForTask,
                            progress: req.status === 'Done' ? 1 : 0,
                            color: color,
                            originalRequest: req,
                        };
                    });
                
                setData({ data: tasks, links: [] });
            } catch (err) {
                setError(err.response?.data || "Не удалось загрузить данные");
            } finally {
                setLoading(false);
            }
        };

        fetchAllRequests();
    }, [filters]);

    // --- Инициализация и настройка диаграммы ---
    useEffect(() => {
        // Локализация (русский язык)
        gantt.i18n.setLocale("ru");

        // Конфигурация колонок
        gantt.config.columns = [
            { name: 'text', label: 'Название заявки', width: '*', tree: true },
            { name: 'start_date', label: 'Начало', align: 'center', width: 90 },
            { name: 'end_date', label: 'Окончание', align: 'center', width: 90 },
        ];
        
        gantt.config.date_grid = "%d.%m.%y";
        gantt.config.readonly = true; // Запрещаем перетаскивание
        gantt.config.row_height = 40;
        
        // Инициализируем диаграмму в указанном контейнере
        gantt.init(ganttContainer.current);
        
        // Привязываем событие клика
        const onTaskClickEvent = gantt.attachEvent("onTaskClick", (id) => {
            const task = gantt.getTask(id);
            if (task && onTaskClick && task.originalRequest) {
                onTaskClick(task.originalRequest);
            }
            return true; // Важно для стандартного поведения
        });

        // Очистка при размонтировании компонента
        return () => {
            gantt.detachEvent(onTaskClickEvent);
            gantt.clearAll();
        };
    }, [onTaskClick]); // Запускаем один раз при монтировании

    // --- Загрузка данных в диаграмму при их изменении ---
    useEffect(() => {
        if (data && gantt) {
            gantt.parse(data);
        }
    }, [data]);

    return (
        <div className="h-[75vh] w-full border rounded-md relative">
            {loading && <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-20"><RefreshCw className="h-8 w-8 text-gray-500 animate-spin" /></div>}
            {error && <div className="absolute inset-0 flex items-center justify-center text-red-600 z-20 bg-white/70"><AlertTriangle className="h-6 w-6 mr-2" /> {error}</div>}
            
            <div ref={ganttContainer} style={{ width: '100%', height: '100%' }}></div>
            
            {!loading && !error && data.data.length === 0 && (
                <div className="absolute inset-0 flex items-center justify-center text-gray-500">Нет заявок для отображения.</div>
            )}
        </div>
    );
}