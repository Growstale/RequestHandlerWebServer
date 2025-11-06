// src/pages/GanttChartView.jsx

import React, { useState, useEffect } from 'react';
import { Gantt, ViewMode } from 'gantt-task-react';
import "gantt-task-react/dist/index.css"; // Стили для новой библиотеки
import { getRequests } from '@/api/requestApi';
import { RefreshCw, AlertTriangle } from 'lucide-react';

export default function GanttChartView({ filters, onTaskClick }) {
    const [tasks, setTasks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [view, setView] = useState(ViewMode.Day); // Состояние для переключения вида (День, Неделя, Месяц)

    // Загрузка данных
    useEffect(() => {
        const fetchAllRequests = async () => {
            setLoading(true);
            setError(null);
            setTasks([]);
            try {
                // Загружаем ВСЕ заявки, соответствующие фильтрам
                const response = await getRequests({ ...filters, page: 0, size: 2000 });
                
                const transformedTasks = response.data.content
                    .filter(req => req.daysForTask > 0)
                    .map(req => {
                        const startDate = new Date(req.createdAt);
                        const endDate = new Date(startDate);
                        endDate.setDate(startDate.getDate() + req.daysForTask);

                        // Определяем стили для полосок в зависимости от статуса
                        let styles = {
                            progressColor: '#3b82f6', // Синий (Выполнена)
                            progressSelectedColor: 'white',
                            backgroundColor: '#3b82f6', // Цвет фона по умолчанию синий
                        };
                        if (req.isOverdue && req.status === 'In work') {
                             styles.progressColor = '#ef4444'; // Красный (Просрочена)
                             styles.backgroundColor = '#ef4444';
                        } else if (req.status === 'In work') {
                             styles.progressColor = '#22c55e'; // Зеленый (В работе)
                             styles.backgroundColor = '#22c55e';
                        }

                        return {
                            start: startDate,
                            end: endDate,
                            name: `#${req.requestID} ${req.shopName} - ${req.description}`,
                            id: req.requestID.toString(),
                            type: 'task',
                            progress: req.status === 'Done' ? 100 : 0,
                            isDisabled: true, // Запрещаем перетаскивание
                            styles: styles,
                        };
                    });
                setTasks(transformedTasks);
            } catch (err) {
                setError(err.response?.data || "Не удалось загрузить данные для диаграммы");
            } finally {
                setLoading(false);
            }
        };

        fetchAllRequests();
    }, [filters]);

    // Компонент для переключения вида
    const ViewSwitcher = ({ onViewModeChange }) => {
        return (
            <div className="flex gap-2 mb-4">
                <button className="text-sm px-3 py-1 border rounded hover:bg-gray-100" onClick={() => onViewModeChange(ViewMode.Day)}>День</button>
                <button className="text-sm px-3 py-1 border rounded hover:bg-gray-100" onClick={() => onViewModeChange(ViewMode.Week)}>Неделя</button>
                <button className="text-sm px-3 py-1 border rounded hover:bg-gray-100" onClick={() => onViewModeChange(ViewMode.Month)}>Месяц</button>
            </div>
        );
    };

    return (
        <div className="h-[75vh] w-full border rounded-md relative">
            {loading && <div className="absolute inset-0 flex items-center justify-center bg-white/70 z-10"><RefreshCw className="h-8 w-8 text-gray-500 animate-spin" /></div>}
            {error && <div className="absolute inset-0 flex items-center justify-center text-red-600 z-10"><AlertTriangle className="h-6 w-6 mr-2" /> {error}</div>}
            
            {!loading && !error && (
                <>
                    <div className="p-2 border-b">
                         <ViewSwitcher onViewModeChange={viewMode => setView(viewMode)} />
                    </div>
                    {tasks.length > 0 ? (
                        <Gantt
                            tasks={tasks}
                            viewMode={view}
                            locale="ru" // Включаем русский язык
                            // Указываем, какие колонки мы хотим видеть
                            listCellWidth={""} // Автоматическая ширина для списка задач
                            columnWidth={65} // Ширина колонки с датой
                            rowHeight={40}
                            ganttHeight={600} // Высота самой диаграммы
                            onClick={(task) => {
                                // Проверяем, что функция была передана, и вызываем ее
                                if (onTaskClick) {
                                    onTaskClick(task.originalRequest);
                                }
                            }}
                            columns={[
                                {
                                    id: 1,
                                    name: 'name',
                                    label: 'Название заявки',
                                    width: 300,
                                },
                                {
                                    id: 2,
                                    name: 'start',
                                    label: 'Дата начала',
                                    width: 90,
                                },
                                {
                                    id: 3,
                                    name: 'end',
                                    label: 'Дата окончания',
                                    width: 90,
                                }
                            ]}
                        />
                    ) : (
                        <div className="absolute inset-0 flex items-center justify-center text-gray-500">Нет заявок для отображения с текущими фильтрами.</div>
                    )}
                </>
            )}
        </div>
    );
}