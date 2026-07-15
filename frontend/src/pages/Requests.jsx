import React, { useState, useEffect, useCallback, useMemo, useRef, Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getRequests, deleteRequest, createRequest, updateRequest, restoreRequest, completeRequest, uploadPhotos, getRequestById } from '@/api/requestApi';
import { getShops } from '@/api/shopApi';
import { getWorkCategories } from '@/api/workCategoryApi';
import { getUrgencyCategories } from '@/api/urgencyCategoryApi';
import { getUsers, getContractors } from '@/api/adminApi';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogDescription } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { BarChart3, List, PlusCircle, Trash2, Edit, MessageSquare, Camera, Search, XCircle, RotateCcw, Eye, ArrowUpDown, Store, CalendarRange } from 'lucide-react';
import Pagination from '@/components/Pagination';
import RequestForm from './RequestForm';
import CommentsModal from './CommentsModal';
import PhotosModal from './PhotosModal';
import RequestDetailsModal from './RequestDetailsModal';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName, getStatusDisplayName } from '@/lib/displayNames'; 
import { logger } from '@/lib/logger';
import { useAuth } from '@/context/AuthProvider'; 
import api from '@/api/axios';
import { useSSE } from '@/hooks/useSSE';

const GanttChartView = lazy(() => import('./GanttChartView'));

const filterKeys = ['searchTerm', 'shopId', 'workCategoryId', 'urgencyId', 'contractorId', 'status', 'overdue', 'startDate', 'endDate', 'closedStartDate', 'closedEndDate'];

export default function Requests({ archived = false }) {
    const { user, accessToken } = useAuth();
    const isAdmin = user?.role === 'RetailAdmin';
    const isModerator = user?.role === 'Moderator';
    const isContractor = user?.role === 'Contractor';
    const isStoreManager = user?.role === 'StoreManager';
    const [viewMode, setViewMode] = useState(user?.role === 'StoreManager' ? 'table' : 'byShop');
    const canCreate = isAdmin || isModerator;    

    const [unreadNotifications, setUnreadNotifications] = useState([]);
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [paginationData, setPaginationData] = useState({ totalPages: 0, totalItems: 0 });

    const [shops, setShops] = useState([]);
    const [workCategories, setWorkCategories] = useState([]);
    const [urgencyCategories, setUrgencyCategories] = useState([]);
    const [contractors, setContractors] = useState([]);
    
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isAlertOpen, setIsAlertOpen] = useState(false);
    const [isCommentsOpen, setIsCommentsOpen] = useState(false);
    const [isPhotosOpen, setIsPhotosOpen] = useState(false);
    const [isDetailsOpen, setIsDetailsOpen] = useState(false);
    const [currentRequest, setCurrentRequest] = useState(null);
    const [formApiError, setFormApiError] = useState(null);

    const [searchParams, setSearchParams] = useSearchParams();

    const [backToDetails, setBackToDetails] = useState(false);

    const searchParamsString = searchParams.toString();

    const areFiltersActive = filterKeys.some(key => searchParams.has(key));
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [isCompleteAlertOpen, setIsCompleteAlertOpen] = useState(false);
    const [isRestoreAlertOpen, setIsRestoreAlertOpen] = useState(false);
    const [targetRequestId, setTargetRequestId] = useState(null);

    const [highlightConfig, setHighlightConfig] = useState({ details: false, comments: false, photos: false });

    const [period, setPeriod] = useState('all');
    const [customStart, setCustomStart] = useState('');
    const [customEnd, setCustomEnd] = useState('');

    const [closedPeriod, setClosedPeriod] = useState('all');
    const [closedCustomStart, setClosedCustomStart] = useState('');
    const [closedCustomEnd, setClosedCustomEnd] = useState('');

const clearAndCheckNotifs = (requestId, keywords) => {
        const toDelete = unreadNotifications.filter(n =>
            n.requestID === requestId &&
            keywords.some(kw => n.title.toLowerCase().includes(kw))
        );

        if (toDelete.length > 0) {
            toDelete.forEach(n => {
                api.delete(`/api/web-notifications/${n.notificationID}`)
                   .then(() => {
                       // Мгновенно говорим колокольчику обновиться
                       window.dispatchEvent(new Event('refresh-notifications'));
                   })
                   .catch(e => console.error(e));
            });
            // Оптимистично убираем из локального стейта, чтобы не ждать SSE
            setUnreadNotifications(prev => prev.filter(n => !toDelete.includes(n)));
            return toDelete;
        }
        return [];
    };

    const reloadRequests = useCallback(async (silent = false) => {
        if (!silent) {
            setLoading(true);
            setError(null);
        }
        const currentParams = new URLSearchParams(searchParamsString);
        try {
            const useDateFilters = viewMode === 'gantt' || archived;
            const params = {
                page: parseInt(currentParams.get('page') || '0', 10),
                archived,
                searchTerm: currentParams.get('searchTerm') || null,
                shopId: currentParams.get('shopId') || null,
                workCategoryId: currentParams.get('workCategoryId') || null,
                urgencyId: currentParams.get('urgencyId') || null,
                contractorId: currentParams.get('contractorId') || null,
                status: currentParams.get('status') || null,
                overdue: currentParams.get('overdue') === 'true',
                startDate: useDateFilters ? (currentParams.get('startDate') || null) : null,
                endDate: useDateFilters ? (currentParams.get('endDate') || null) : null,
                closedStartDate: searchParams.get('closedStartDate') || null,
                closedEndDate: searchParams.get('closedEndDate') || null,

                sortConfig: (currentParams.getAll('sort').length > 0 ? currentParams.getAll('sort') : ['requestID,asc']).map(s => ({
                    field: s.split(',')[0],
                    direction: s.split(',')[1] || 'asc'
                }))
            };
            const response = await getRequests(params);
            setRequests(response.data.content);
            setPaginationData({ totalPages: response.data.totalPages, totalItems: response.data.totalItems });

            setCurrentRequest(prevReq => {
                if (prevReq) {
                    const updatedReq = response.data.content.find(r => r.requestID === prevReq.requestID);
                    return updatedReq || prevReq;
                }
                return prevReq;
            });
        } catch (err) {
            if (!silent) setError(err.response?.data || `Не удалось загрузить ${archived ? 'архив' : 'заявки'}`);
        } finally {
            if (!silent) setLoading(false);
        }
    }, [archived, searchParamsString, viewMode]);

    const reloadRef = useRef(reloadRequests);

    const fetchUnreadNotifications = useCallback(async () => {
        try {
            const res = await api.get(`/api/web-notifications?t=${new Date().getTime()}`);
            setUnreadNotifications(res.data);
        } catch (e) {
            console.error("Ошибка загрузки уведомлений", e);
        }
    }, []);

    useEffect(() => {
        fetchUnreadNotifications();
    }, [fetchUnreadNotifications]);

    // Синхронизация: обновляем обводку в каталоге, если уведомление удалили из колокольчика
    useEffect(() => {
        const handleRefresh = () => fetchUnreadNotifications();
        window.addEventListener('refresh-notifications', handleRefresh);
        return () => window.removeEventListener('refresh-notifications', handleRefresh);
    }, [fetchUnreadNotifications]);

    const groupedRequests = useMemo(() => {
        if (viewMode !== 'byShop') return {};
        return requests.reduce((acc, req) => {
            const shopName = req.shopName || 'Без магазина';
            if (!acc[shopName]) acc[shopName] = [];
            acc[shopName].push(req);
            return acc;
        }, {});
    }, [viewMode, requests]);

    useEffect(() => {
        if (!archived) return;

        const today = new Date();
        const endStr = today.toISOString().split('T')[0];
        
        // Расчет для даты создания
        let startStr = '';
        const startObj = new Date(today);
        switch (period) {
            case 'today': startStr = endStr; break;
            case 'week': startObj.setDate(today.getDate() - 7); startStr = startObj.toISOString().split('T')[0]; break;
            case 'month': startObj.setMonth(today.getMonth() - 1); startStr = startObj.toISOString().split('T')[0]; break;
            case 'quarter': startObj.setMonth(today.getMonth() - 3); startStr = startObj.toISOString().split('T')[0]; break;
            case 'half_year': startObj.setMonth(today.getMonth() - 6); startStr = startObj.toISOString().split('T')[0]; break;
            case 'year': startObj.setFullYear(today.getFullYear() - 1); startStr = startObj.toISOString().split('T')[0]; break;
            case 'custom': startStr = customStart; break;
            default: startStr = ''; break;
        }

        // Расчет для даты закрытия
        let cStartStr = '';
        const cStartObj = new Date(today);
        switch (closedPeriod) {
            case 'today': cStartStr = endStr; break;
            case 'week': cStartObj.setDate(today.getDate() - 7); cStartStr = cStartObj.toISOString().split('T')[0]; break;
            case 'month': cStartObj.setMonth(today.getMonth() - 1); cStartStr = cStartObj.toISOString().split('T')[0]; break;
            case 'all': cStartStr = ''; break;
            case 'custom': cStartStr = closedCustomStart; break;
            default: cStartStr = ''; break;
        }

        setSearchParams(prev => {
            // Обработка даты создания
            if (period === 'all') {
                prev.delete('startDate');
                prev.delete('endDate');
            } else if (period === 'custom') {
                if (customStart) prev.set('startDate', customStart);
                if (customEnd) prev.set('endDate', customEnd);
            } else {
                prev.set('startDate', startStr);
                prev.set('endDate', endStr);
            }

            // Обработка даты закрытия
            if (closedPeriod === 'all') {
                prev.delete('closedStartDate');
                prev.delete('closedEndDate');
            } else if (closedPeriod === 'custom') {
                if (closedCustomStart) prev.set('closedStartDate', closedCustomStart);
                if (closedCustomEnd) prev.set('closedEndDate', closedCustomEnd);
            } else {
                prev.set('closedStartDate', cStartStr);
                prev.set('closedEndDate', endStr);
            }

            prev.set('page', '0');
            return prev;
        }, { replace: true });
    }, [period, customStart, customEnd, closedPeriod, closedCustomStart, closedCustomEnd, archived]);

    useEffect(() => {
        reloadRef.current = reloadRequests;
    }, [reloadRequests]);

    useSSE(useCallback((message) => {
        if (message === "REQUESTS_UPDATED") {
            reloadRef.current(true);
        }
        if (message === `WEB_NOTIFICATION_USER_${user?.id}`) {
            // Увеличиваем задержку до 500мс и обходим кэш
            setTimeout(() => fetchUnreadNotifications(), 500);
        }
    }, [user?.id, fetchUnreadNotifications]));

    useEffect(() => {
        if (!searchParams.has('sort')) {
            setSearchParams(prev => {
                prev.set('sort', 'requestID,asc');
                return prev;
            }, { replace: true });
        }
    }, []);

    const handleResetFilters = () => {
        setPeriod('all');
        setCustomStart('');
        setCustomEnd('');
        setClosedPeriod('all');
        setClosedCustomStart('');
        setClosedCustomEnd('');

        setSearchParams(prev => {
            filterKeys.forEach(key => prev.delete(key));
            prev.set('page', '0');
            return prev;
        }, { replace: true });
    };

    const currentFilters = useMemo(() => ({
        archived,
        searchTerm: searchParams.get('searchTerm') || null,
        shopId: searchParams.get('shopId') || null,
        workCategoryId: searchParams.get('workCategoryId') || null,
        urgencyId: searchParams.get('urgencyId') || null,
        contractorId: searchParams.get('contractorId') || null,
        status: searchParams.get('status') || null,
        overdue: searchParams.get('overdue') === 'true',
        startDate: (viewMode === 'gantt' || archived) ? (searchParams.get('startDate') || null) : null,
        endDate: (viewMode === 'gantt' || archived) ? (searchParams.get('endDate') || null) : null,
        closedStartDate: archived ? (searchParams.get('closedStartDate') || null) : null,
        closedEndDate: archived ? (searchParams.get('closedEndDate') || null) : null,
        sortConfig: (searchParams.getAll('sort').length > 0 ? searchParams.getAll('sort') : ['requestID,asc']).map(s => ({
            field: s.split(',')[0],
            direction: s.split(',')[1] || 'asc'
        }))
    }), [archived, searchParamsString, viewMode]);

    const startDate = searchParams.get('startDate') || '';
    const endDate = searchParams.get('endDate') || '';

    const handleResetSort = () => {
        setSearchParams(prev => {
            prev.delete('sort');
            prev.set('sort', 'requestID,asc');
            return prev;
        }, { replace: true });
    };

    const handleSort = (clickedField, e) => {
        const isShiftPressed = e.shiftKey;
        const currentSortParams = searchParams.getAll('sort');

        setSearchParams(prev => {
            let newSort = [];
            const existingSortIndex = currentSortParams.findIndex(s => s.startsWith(clickedField + ','));

            if (!isShiftPressed) {
                if (existingSortIndex > -1) {
                    const direction = currentSortParams[existingSortIndex].split(',')[1];
                    newSort = [`${clickedField},${direction === 'asc' ? 'desc' : 'asc'}`];
                } else {
                    newSort = [`${clickedField},asc`];
                }
            } else {
                newSort = [...currentSortParams];
                if (existingSortIndex > -1) {
                    const direction = newSort[existingSortIndex].split(',')[1];
                    newSort[existingSortIndex] = `${clickedField},${direction === 'asc' ? 'desc' : 'asc'}`;
                } else {
                    newSort.push(`${clickedField},asc`);
                }
            }

            prev.delete('sort');
            newSort.forEach(s => prev.append('sort', s));
            prev.set('page', '0');
            return prev;
        }, { replace: true });
    };

    const handleRestore = async (requestId) => {
        try {
            await restoreRequest(requestId);
            setRequests(prev => prev.filter(r => r.requestID !== requestId));
            setIsRestoreAlertOpen(false); // Закрыть окно
        } catch (err) {
            setError(err.response?.data || 'Ошибка восстановления');
        }
    };

    const updateQueryParam = (key, value) => {
        setSearchParams(prev => {
            if (value === 'ALL' || value === '' || value === false) {
                prev.delete(key);
            } else {
                prev.set(key, value);
            }

            if (key !== 'page') {
                prev.set('page', '0');
            }

            return prev;
        }, { replace: true });
    };

    const handleComplete = async (requestId) => {
        try {
            await completeRequest(requestId);
            setRequests(prev => prev.filter(r => r.requestID !== requestId));
        } catch (err) {
            setError(err.response?.data || 'Ошибка завершения');
        }
    };

    const SortableHeader = ({ field, children }) => {
        const sort = searchParams.getAll('sort');
        const sortParam = sort.find(s => s.startsWith(field + ','));
        const sortIndex = sort.findIndex(s => s.startsWith(field + ','));
        const direction = sortParam ? sortParam.split(',')[1] : null;
        const directionIcon = direction === 'asc' ? '↓' : (direction === 'desc' ? '↑' : '');

        return (
            <TableHead
                className="cursor-pointer select-none transition-colors hover:bg-gray-100"
                onClick={(e) => handleSort(field, e)}
            >
                <div className={cn("flex items-center gap-2", { "text-blue-600 font-bold": sortParam })}>
                    {children}
                    {sortParam ? (
                        <span className="flex items-center gap-1">
                            {directionIcon}
                            {sort.length > 1 && (
                                <span className="text-xs font-semibold text-white bg-blue-500 rounded-full w-4 h-4 flex items-center justify-center">
                                    {sortIndex + 1}
                                </span>
                            )}
                        </span>
                    ) : ( <ArrowUpDown className="h-4 w-4 opacity-30"/> )}
                </div>
            </TableHead>
        );
    };

    useEffect(() => {
        const fetchFiltersData = async () => {
            try {
                const [workCatsRes, urgencyCatsRes, contractorsRes] = await Promise.all([
                    getWorkCategories({ size: 1000 }),
                    getUrgencyCategories(),
                    getContractors() 
                ]);

                setWorkCategories(workCatsRes.data.content);
                setUrgencyCategories(urgencyCatsRes.data);
                setContractors(contractorsRes.data);

                        if (isAdmin || isModerator) {
                            const shopsRes = await getShops({ size: 1000 });
                            setShops(shopsRes.data.content);
                        }
                    } catch (error) {
                        console.error("Failed to fetch filter data", error);
                        setError("Не удалось загрузить данные для фильтров.");
                    }
                };
        
                if (user) {
                    fetchFiltersData();
                }
            }, [user, isAdmin, isModerator]); 


    useEffect(() => {
        reloadRequests();
    }, [reloadRequests]);


    // ИЗМЕНЕННАЯ ФУНКЦИЯ: ТЕПЕРЬ ПРИНИМАЕТ ФАЙЛЫ
    const handleFormSubmit = async (formData, files = []) => {
        setFormApiError(null);
        setIsSubmitting(true);
        try {
            if (currentRequest) {
                await updateRequest(currentRequest.requestID, formData);
            } else {
                const response = await createRequest(formData);
                const newRequestId = response.data.requestID;
                
                // Если есть файлы, сразу их загружаем
                if (files && files.length > 0) {
                    try {
                        await uploadPhotos(newRequestId, files);
                    } catch (photoErr) {
                        console.error("Ошибка при загрузке фото во время создания:", photoErr);
                        // Не прерываем процесс, заявка уже создана, просто выводим ошибку в консоль
                    }
                }
            }
            setIsFormOpen(false);
            await reloadRequests(true); 
            
        } catch (err) {
            console.error("Ошибка при отправке формы заявки:", err.response || err);
            setFormApiError(err.response?.data || 'Произошла ошибка. Проверьте консоль для деталей.');
        } finally {
            setIsSubmitting(false);
        }
    };

    useEffect(() => {
        const openId = searchParams.get('openId');
        if (openId) {
            const reqId = parseInt(openId);
            const reqFromList = requests.find(r => r.requestID === reqId);
            
            const handleOpen = (req) => {
                setCurrentRequest(req);
                const deletedNotifs = clearAndCheckNotifs(req.requestID, ['обновлен', 'выполнен', 'новая', 'восстановлен', 'просроч']);
                
                const allMessages = deletedNotifs.map(n => n.message?.toLowerCase() || "").join(" ");
                
                setHighlightConfig(prev => ({ 
                    ...prev, 
                    details: {
                        status: allMessages.includes('статус'),
                        urgency: allMessages.includes('срочность'),
                        contractor: allMessages.includes('исполнитель'),
                        shop: allMessages.includes('магазин'),
                        description: allMessages.includes('описание'),
                        overdue: deletedNotifs.some(n => n.title.toLowerCase().includes('просроч')),
                        restored: deletedNotifs.some(n => n.title.toLowerCase().includes('восстановлен'))
                    } 
                }));
                setIsDetailsOpen(true);
                setSearchParams(prev => { prev.delete('openId'); return prev; }, { replace: true });
            };

            if (reqFromList) {
                handleOpen(reqFromList);
            } else if (!loading) {
                getRequestById(reqId).then(res => {
                    handleOpen(res.data);
                }).catch(err => {
                    console.error("Не удалось загрузить заявку по ID", err);
                    setSearchParams(prev => { prev.delete('openId'); return prev; }, { replace: true });
                });
            }
        }
    }, [searchParams, requests, loading, setSearchParams]);

    const handleDeleteConfirm = async () => {
        if (!currentRequest) return;
        try {
            await deleteRequest(currentRequest.requestID);
            setIsAlertOpen(false);
            reloadRequests(true); 
        } catch (err) {
            console.error("Ошибка удаления:", err.response?.data);
            setIsAlertOpen(false);
        }
    };
    
    const openDetails = useCallback((req) => {
        setCurrentRequest(req);
        const deletedNotifs = clearAndCheckNotifs(req.requestID, ['обновлен', 'выполнен', 'новая', 'восстановлен', 'просроч']);
        
        const allMessages = deletedNotifs.map(n => n.message?.toLowerCase() || "").join(" ");
        
        setHighlightConfig(prev => ({ 
            ...prev, 
            details: {
                status: allMessages.includes('статус'),
                urgency: allMessages.includes('срочность'),
                contractor: allMessages.includes('исполнитель'),
                shop: allMessages.includes('магазин'),
                description: allMessages.includes('описание'),
                overdue: deletedNotifs.some(n => n.title.toLowerCase().includes('просроч')),
                restored: deletedNotifs.some(n => n.title.toLowerCase().includes('восстановлен'))
            } 
        }));
        setIsDetailsOpen(true);
    }, [unreadNotifications]);

    const openCreateForm = () => { setCurrentRequest(null); setFormApiError(null); setIsFormOpen(true); };
    const openEditForm = (req) => { setCurrentRequest(req); setFormApiError(null); setIsFormOpen(true); };
    const openDeleteAlert = (req) => { setCurrentRequest(req); setIsAlertOpen(true); };
    const openComments = (req) => { 
        setCurrentRequest(req); 
        const deletedNotifs = clearAndCheckNotifs(req.requestID, ['комментарий']);
        setHighlightConfig(prev => ({ ...prev, comments: deletedNotifs.length > 0 }));
        setIsCommentsOpen(true); 
    };
    
    const openPhotos = (req) => { 
        setCurrentRequest(req); 
        const deletedNotifs = clearAndCheckNotifs(req.requestID, ['фото']);
        setHighlightConfig(prev => ({ ...prev, photos: deletedNotifs.length > 0 }));
        setIsPhotosOpen(true); 
    };

    const openCommentsFromDetails = (req) => {
        setBackToDetails(true);
        setIsDetailsOpen(false);
        setCurrentRequest(req);
        const deletedNotifs = clearAndCheckNotifs(req.requestID, ['комментарий']);
        setHighlightConfig(prev => ({ ...prev, comments: deletedNotifs.length > 0 }));
        setIsCommentsOpen(true);
    };

    const openPhotosFromDetails = (req) => {
        setBackToDetails(true);
        setIsDetailsOpen(false);
        setCurrentRequest(req);
        const deletedNotifs = clearAndCheckNotifs(req.requestID, ['фото']);
        setHighlightConfig(prev => ({ ...prev, photos: deletedNotifs.length > 0 }));
        setIsPhotosOpen(true);
    };

    const handleCommentsModalClose = () => {
        setIsCommentsOpen(false);
        if (backToDetails) {
            setIsDetailsOpen(true);
            setBackToDetails(false);
        }
        reloadRequests(true);
    };

    const handlePhotosModalClose = () => {
        setIsPhotosOpen(false);
        if (backToDetails) {
            setIsDetailsOpen(true);
            setBackToDetails(false);
        }
        reloadRequests(true);
    };
    
    const page = parseInt(searchParams.get('page') || '0', 10);
    const searchTerm = searchParams.get('searchTerm') || '';
    const shopId = searchParams.get('shopId') || 'ALL';
    const workCategoryId = searchParams.get('workCategoryId') || 'ALL';
    const urgencyId = searchParams.get('urgencyId') || 'ALL';
    const contractorId = searchParams.get('contractorId') || 'ALL';
    const status = searchParams.get('status') || 'ALL';
    const overdue = searchParams.get('overdue') === 'true';
    const sort = searchParams.getAll('sort');

    return (
        <main className="container mx-auto p-6">
            <div className="flex flex-col xl:flex-row justify-between items-start xl:items-center gap-4 mb-4">
    <h1 className="text-2xl md:text-3xl font-semibold">{archived ? 'Архив заявок' : 'Управление заявками'}</h1>
    
    <div className="grid grid-cols-2 md:flex md:flex-wrap items-center gap-2 w-full xl:w-auto">
    {!isStoreManager && (
        <>
            <Button variant={viewMode === 'table' ? 'secondary' : 'outline'} onClick={() => setViewMode('table')} className="w-full text-xs sm:text-sm px-2 md:w-auto">
                <List className="mr-1 sm:mr-2 h-4 w-4 shrink-0" /> Таблица
            </Button>
            <Button variant={viewMode === 'gantt' ? 'secondary' : 'outline'} onClick={() => setViewMode('gantt')} className="w-full text-xs sm:text-sm px-2 md:w-auto">
                <BarChart3 className="mr-1 sm:mr-2 h-4 w-4 shrink-0" /> Диаграмма
            </Button>
            <Button variant={viewMode === 'byShop' ? 'secondary' : 'outline'} onClick={() => setViewMode('byShop')} className="w-full col-span-2 md:w-auto text-xs sm:text-sm px-2">
                <Store className="mr-1 sm:mr-2 h-4 w-4 shrink-0" /> По магазинам
            </Button>
        </>
    )}

        {canCreate  && !archived && (
            <>
                <Button onClick={openCreateForm} className="w-full col-span-2 md:w-auto">
                    <PlusCircle className="mr-2 h-4 w-4 shrink-0" /> Создать заявку
                </Button>
                <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
                    <DialogContent className="max-w-3xl w-[95vw] sm:w-full max-h-[95vh] flex flex-col overflow-hidden p-4 sm:p-6 rounded-xl">
                                    <DialogHeader>
                                        <DialogTitle>{currentRequest ? 'Редактировать заявку' : 'Новая заявка'}</DialogTitle>
                                        <DialogDescription className="hidden">Форма создания или редактирования заявки</DialogDescription>
                                    </DialogHeader>
                                    <RequestForm
                                        key={currentRequest ? currentRequest.requestID : 'new'}
                                        currentRequest={currentRequest}
                                        onSubmit={handleFormSubmit}
                                        onCancel={() => setIsFormOpen(false)}
                                        apiError={formApiError}
                                        shops={shops}
                                        workCategories={workCategories}
                                        urgencyCategories={urgencyCategories}
                                        contractors={contractors}
                                        isSubmitting={isSubmitting}
                                    />
                                </DialogContent>
                            </Dialog>
                        </>
                    )}
                </div>

            </div>

            <div className="flex flex-wrap items-center gap-2 md:gap-4 mb-4">
                {(sort.length > 1 || (sort.length === 1 && sort[0] !== 'requestID,asc')) && (
                    <Button variant="outline" onClick={handleResetSort}>
                        <XCircle className="mr-2 h-4 w-4" />Сбросить сортировку
                    </Button>
                )}
                {areFiltersActive && (
                    <Button variant="outline" onClick={handleResetFilters}>
                        <XCircle className="mr-2 h-4 w-4" />Сбросить фильтры
                    </Button>
                )}
            </div>

<div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3 mb-4 p-4 border rounded-lg bg-gray-50 items-end">
                 
            <div className="space-y-1">
                <Label className="text-xs text-muted-foreground ml-1">Поиск</Label>
                <Input placeholder="Поиск..." value={searchTerm} onChange={e => updateQueryParam('searchTerm', e.target.value)} className="bg-white" />
            </div>

            {archived && (
                <>
                {/* Дата создания */}
                <div className={cn("space-y-1 transition-all", period === 'custom' ? "md:col-span-2" : "")}>
                    <Label className="text-xs text-muted-foreground ml-1">Дата создания</Label>
                    <div className="flex flex-col xl:flex-row xl:items-center bg-white border rounded-md min-h-[36px] gap-1 p-1">
                        <div className="flex items-center w-full xl:w-auto">
                            <CalendarRange className="h-4 w-4 text-gray-500 mx-2 hidden xl:block shrink-0" />
                            <Select value={period} onValueChange={setPeriod}>
                                <SelectTrigger className="w-full xl:w-[130px] border-none shadow-none focus:ring-0 bg-transparent h-8 text-xs">
                                    <SelectValue placeholder="Период" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="today">За сегодня</SelectItem>
                                    <SelectItem value="week">За 7 дней</SelectItem>
                                    <SelectItem value="month">За месяц</SelectItem>
                                    <SelectItem value="all">За всё время</SelectItem>
                                    <SelectItem value="custom">Свой период...</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        
                        {period === 'custom' && (
                            <div className="flex items-center gap-1 w-full xl:w-auto xl:border-l border-gray-100 xl:pl-2">
                                <Input type="date" value={customStart} onChange={e => setCustomStart(e.target.value)} className="h-8 text-xs flex-1 xl:w-[120px] border-gray-200" />
                                <span className="text-gray-300 hidden xl:inline">—</span>
                                <Input type="date" value={customEnd} onChange={e => setCustomEnd(e.target.value)} className="h-8 text-xs flex-1 xl:w-[120px] border-gray-200" />
                            </div>
                        )}
                    </div>
                </div>

                {/* Дата закрытия (архивации) */}
                <div className={cn("space-y-1 transition-all", closedPeriod === 'custom' ? "md:col-span-2" : "")}>
                    <Label className="text-xs text-muted-foreground ml-1">Дата закрытия (архивации)</Label>
                    <div className="flex flex-col xl:flex-row xl:items-center bg-white border rounded-md min-h-[36px] gap-1 p-1">
                        <div className="flex items-center w-full xl:w-auto">
                            <CalendarRange className="h-4 w-4 text-gray-500 mx-2 hidden xl:block shrink-0" />
                            <Select value={closedPeriod} onValueChange={setClosedPeriod}>
                                <SelectTrigger className="w-full xl:w-[130px] border-none shadow-none focus:ring-0 bg-transparent h-8 text-xs">
                                    <SelectValue placeholder="Период" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="today">За сегодня</SelectItem>
                                    <SelectItem value="week">За 7 дней</SelectItem>
                                    <SelectItem value="month">За месяц</SelectItem>
                                    <SelectItem value="all">За всё время</SelectItem>
                                    <SelectItem value="custom">Свой период...</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        
                        {closedPeriod === 'custom' && (
                            <div className="flex items-center gap-1 w-full xl:w-auto xl:border-l border-gray-100 xl:pl-2">
                                <Input type="date" value={closedCustomStart} onChange={e => setClosedCustomStart(e.target.value)} className="h-8 text-xs flex-1 xl:w-[120px] border-gray-200" />
                                <span className="text-gray-300 hidden xl:inline">—</span>
                                <Input type="date" value={closedCustomEnd} onChange={e => setClosedCustomEnd(e.target.value)} className="h-8 text-xs flex-1 xl:w-[120px] border-gray-200" />
                            </div>
                        )}
                    </div>
                </div>
                </>
            )}

                 {viewMode === 'gantt' && (
                    <>
                        <div className="space-y-1">
                            <Label className="text-xs text-muted-foreground ml-1">Дата начала</Label>
                            <Input 
                                type="date" 
                                value={startDate} 
                                onChange={e => updateQueryParam('startDate', e.target.value)} 
                                className="bg-white"
                            />
                        </div>

                        <div className="space-y-1">
                            <Label className="text-xs text-muted-foreground ml-1">Дата окончания</Label>
                            <Input 
                                type="date" 
                                value={endDate} 
                                onChange={e => updateQueryParam('endDate', e.target.value)} 
                                className="bg-white"
                            />
                        </div>
                    </>
                 )}


                 {(isAdmin || isModerator) && (
                    <div className="space-y-1">
                        <Label className="text-xs text-muted-foreground ml-1">Магазин</Label>
                        <Select onValueChange={(v) => updateQueryParam('shopId', v)} value={shopId}>
                            <SelectTrigger className="bg-white"><SelectValue placeholder="Магазин" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все магазины</SelectItem>
                                {shops.map(s => <SelectItem key={s.shopID} value={s.shopID.toString()}>{s.shopName}</SelectItem>)}
                            </SelectContent>
                        </Select>
                    </div>
                 )}

                <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground ml-1">Вид работы</Label>
                    <Select onValueChange={(v) => updateQueryParam('workCategoryId', v)} value={workCategoryId}>
                        <SelectTrigger className="bg-white"><SelectValue placeholder="Вид работы" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Все виды</SelectItem>
                            {workCategories.map(wc => <SelectItem key={wc.workCategoryID} value={wc.workCategoryID.toString()}>{wc.workCategoryName}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>

                <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground ml-1">Срочность</Label>
                    <Select onValueChange={(v) => updateQueryParam('urgencyId', v)} value={urgencyId}>
                        <SelectTrigger className="bg-white"><SelectValue placeholder="Срочность" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Вся срочность</SelectItem>
                            {urgencyCategories.map(uc => <SelectItem key={uc.urgencyID} value={uc.urgencyID.toString()}>{getUrgencyDisplayName(uc.urgencyName)}</SelectItem>)}
                        </SelectContent>
                    </Select>
                </div>

                {!archived && (
                    <div className="space-y-1">
                        <Label className="text-xs text-muted-foreground ml-1">Статус</Label>
                        <Select onValueChange={(v) => updateQueryParam('status', v)} value={status}>
                            <SelectTrigger className="bg-white"><SelectValue placeholder="Статус" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все статусы</SelectItem>
                                <SelectItem value="In work">В работе</SelectItem>
                                <SelectItem value="Done">Выполнена</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>
                )}

                {(isAdmin || isModerator) && (
                    <div className="space-y-1">
                        <Label className="text-xs text-muted-foreground ml-1">Подрядчик</Label>
                        <Select onValueChange={(v) => updateQueryParam('contractorId', v)} value={contractorId}>
                            <SelectTrigger className="bg-white"><SelectValue placeholder="Подрядчик" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все Подрядчики</SelectItem>
                                {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                            </SelectContent>
                        </Select>
                    </div>
                )}

                {!archived && (
                    <div className="flex items-center space-x-2 p-2 rounded-md justify-start h-10">
                        <input
                            type="checkbox"
                            id="overdue-filter"
                            checked={overdue}
                            onChange={e => updateQueryParam('overdue', e.target.checked)}
                            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 cursor-pointer"
                        />
                        <Label htmlFor="overdue-filter" className="text-sm font-medium text-gray-700 select-none cursor-pointer">
                            Просрочено
                        </Label>
                    </div>
                )}
            </div>

            {loading && <p>Загрузка...</p>}
            {error && <p className="text-red-500">{error}</p>}
            
            {!loading && !error && (
                <>
                    {viewMode === 'table' && (
                        <>
                            <div className="rounded-md border">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableHeader field="requestID" className="w-[80px]">ID</SortableHeader>
                                
                                <SortableHeader field="description" className="w-full min-w-[300px]">
                                    Описание
                                </SortableHeader>
                                
                                <SortableHeader field="shopName" className="min-w-[150px]">Магазин</SortableHeader>
                                <SortableHeader field="workCategoryName" className="min-w-[150px]">Вид работы</SortableHeader>
                                <SortableHeader field="urgencyName" className="w-[120px]">Срочность</SortableHeader>
                                <SortableHeader field="assignedContractorName" className="w-[150px]">Подрядчик</SortableHeader>
                                <SortableHeader field="status" className="w-[120px]">Статус</SortableHeader>
                                <SortableHeader field="daysRemaining" className="w-[80px]">Срок</SortableHeader>
                                
                                <TableHead className="w-[150px] text-right">Действия</TableHead>
                            </TableRow>
                        </TableHeader>
                            <TableBody>
                                {requests.map(req => {
                                    const notifsForReq = unreadNotifications.filter(n => n.requestID === req.requestID);
                                    const hasUnread = notifsForReq.length > 0;
                                    const hasNewComments = notifsForReq.some(n => n.title.toLowerCase().includes('комментарий'));
                                    const hasNewPhotos = notifsForReq.some(n => n.title.toLowerCase().includes('фото'));
                                    const hasUpdate = notifsForReq.some(n => {
                                        const t = n.title.toLowerCase();
                                        return t.includes('обновлен') || t.includes('выполнен') || t.includes('новая') || t.includes('восстановлен') || t.includes('просроч');
                                    });

                                    return (
                                    <TableRow key={req.requestID} className={cn("transition-all duration-300", { 
                                        'bg-red-100': req.isOverdue && req.status === 'In work',
                                        'bg-blue-100': req.status === 'Done',
                                        'border-l-4 border-l-yellow-400': hasUnread
                                    })}>
                                    <TableCell>{req.requestID}</TableCell>
                                    <TableCell className="max-w-0 w-full">
                                        <div 
                                            className="line-clamp-2 break-words text-sm" 
                                            title={req.description}
                                        >
                                            {req.description}
                                        </div>
                                    </TableCell>
                                    <TableCell>{req.shopName}</TableCell>
                                    <TableCell>{req.workCategoryName}</TableCell>
                                    <TableCell>{getUrgencyDisplayName(req.urgencyName)}</TableCell>
                                    <TableCell>{req.assignedContractorName || '—'}</TableCell>
                                    <TableCell>{getStatusDisplayName(req.status)}</TableCell>
                                    <TableCell className={cn({ 
                                        'font-bold text-red-600': req.isOverdue && req.urgencyName !== 'Notes', 
                                        'text-green-600': req.daysRemaining > 0 && req.urgencyName !== 'Notes' && !req.isOverdue
                                    })}>
                                        {req.urgencyName === 'Notes' ? '—' : (req.daysRemaining !== null ? req.daysRemaining : '—')}
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex gap-1">
                                            <Button variant="ghost" size="icon" className={cn("px-2", hasUpdate ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-indigo-700")} onClick={() => openDetails(req)} title="Просмотр деталей">
                                                <Eye className="h-4 w-4"/>
                                            </Button>
                                            <Button variant="ghost" size="sm" className={cn("px-2", hasNewComments ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-blue-700")} onClick={() => openComments(req)} title="Комментарии">
                                                <MessageSquare className="h-4 w-4 mr-1.5"/>
                                                <span className="text-xs font-semibold">{req.commentCount}</span>
                                            </Button>
                                            <Button variant="ghost" size="sm" className={cn("px-2", hasNewPhotos ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-indigo-700")} onClick={() => openPhotos(req)} title="Фотографии">
                                                <Camera className="h-4 w-4 mr-1.5"/>
                                                <span className="text-xs font-semibold">{req.photoCount}</span>
                                            </Button>

                                            {isContractor && req.status === 'In work' && !archived && (
                                                <Button variant="outline" size="sm" className="px-2 hover:text-indigo-700" 
                                                    onClick={() => { setTargetRequestId(req.requestID); setIsCompleteAlertOpen(true); }} 
                                                    title="Завершить заявку">
                                                    Завершить
                                                </Button>
                                            )}

                                            {(isAdmin || isModerator) && req.status !== 'Closed' && (
                                                <Button variant="outline" size="icon" className="px-2 hover:text-indigo-700" onClick={() => openEditForm(req)} title="Редактировать"><Edit className="h-4 w-4" /></Button>
                                            )}

                                            {(isAdmin || isModerator) && archived && req.status === 'Closed' && (
                                                <Button 
                                                    variant="outline" 
                                                    size="icon" 
                                                    className="px-2 hover:text-indigo-700" 
                                                    onClick={() => { 
                                                        setTargetRequestId(req.requestID);
                                                        setIsRestoreAlertOpen(true);
                                                    }} 
                                                    title="Восстановить"
                                                >
                                                    <RotateCcw className="h-4 w-4" />
                                                </Button>
                                            )}
                                            
                                            {(isAdmin || isModerator) && (
                                                <Button variant="destructive" size="icon" className="px-2 hover:text-indigo-700" onClick={() => openDeleteAlert(req)} title="Удалить"><Trash2 className="h-4 w-4" /></Button>
                                            )}
                                                </div>
                                            </TableCell>

                                        </TableRow>
                                            );
                                        })}
                                </TableBody>
                            </Table>
                            </div>
                            <Pagination 
                                currentPage={page}
                                totalPages={paginationData.totalPages}
                                onPageChange={(p) => updateQueryParam('page', p)}
                            />
                        </>
                    )}

                    {viewMode === 'gantt' && (
                        <Suspense fallback={<div className="h-[75vh] w-full border rounded-md flex items-center justify-center text-gray-500">Загрузка диаграммы...</div>}>
                            <GanttChartView 
                                filters={currentFilters} 
                                onTaskClick={openDetails}
                            />
                        </Suspense>
                    )}

                    {viewMode === 'byShop' && (
                        <div className="space-y-8">
                            {Object.keys(groupedRequests).sort().map(shopName => (
                                <div key={shopName}>
                                    <h2 className="text-xl font-bold mb-2 border-b pb-2">{shopName}</h2>
                                    <div className="rounded-md border">
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <SortableHeader field="requestID" className="w-[80px]">ID</SortableHeader>
                                                    <SortableHeader field="description" className="w-full min-w-[300px]">Описание</SortableHeader>
                                                    {/* УДАЛЕНО: Магазин здесь не нужен */}
                                                    <SortableHeader field="workCategoryName" className="min-w-[150px]">Вид работы</SortableHeader>
                                                    <SortableHeader field="urgencyName" className="w-[120px]">Срочность</SortableHeader>
                                                    <SortableHeader field="assignedContractorName" className="w-[150px]">Подрядчик</SortableHeader>
                                                    <SortableHeader field="status" className="w-[120px]">Статус</SortableHeader>
                                                    <SortableHeader field="daysRemaining" className="w-[80px]">Срок</SortableHeader>
                                                    <TableHead className="w-[150px] text-right">Действия</TableHead>
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {groupedRequests[shopName].map(req => {
                                                    const notifsForReq = unreadNotifications.filter(n => n.requestID === req.requestID);
                                                    const hasUnread = notifsForReq.length > 0;
                                                    const hasNewComments = notifsForReq.some(n => n.title.toLowerCase().includes('комментарий'));
                                                    const hasNewPhotos = notifsForReq.some(n => n.title.toLowerCase().includes('фото'));
                                                    const hasUpdate = notifsForReq.some(n => {
                                                        const t = n.title.toLowerCase();
                                                        return t.includes('обновлен') || t.includes('выполнен') || t.includes('новая') || t.includes('восстановлен') || t.includes('просроч');
                                                    });

                                                    return (
                                                            <TableRow key={req.requestID} className={cn("transition-all duration-300", { 
                                                                'bg-red-100': req.isOverdue && req.status === 'In work',
                                                                'bg-blue-100': req.status === 'Done',
                                                                'border-l-4 border-l-yellow-400': hasUnread
                                                            })}>
                                                            <TableCell>{req.requestID}</TableCell>
                                                            <TableCell className="max-w-0 w-full">
                                                                <div 
                                                                    className="line-clamp-2 break-words text-sm" 
                                                                    title={req.description}
                                                                >
                                                                    {req.description}
                                                                </div>
                                                            </TableCell>
                                                            <TableCell>{req.workCategoryName}</TableCell>
                                                            <TableCell>{getUrgencyDisplayName(req.urgencyName)}</TableCell>
                                                            <TableCell>{req.assignedContractorName || '—'}</TableCell>
                                                            <TableCell>{getStatusDisplayName(req.status)}</TableCell>
                                                            <TableCell className={cn({ 'font-bold text-red-600': req.isOverdue, 'text-green-600': req.daysRemaining > 0 && !req.isOverdue })}>
                                                                {req.daysRemaining !== null ? req.daysRemaining : '—'}
                                                            </TableCell>
                                                            <TableCell>
                                                                <div className="flex gap-1">
                                                                    <Button variant="ghost" size="icon" className={cn("px-2", hasUpdate ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-indigo-700")} onClick={() => openDetails(req)} title="Просмотр деталей">
                                                                        <Eye className="h-4 w-4"/>
                                                                    </Button>
                                                                    <Button variant="ghost" size="sm" className={cn("px-2", hasNewComments ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-blue-700")} onClick={() => openComments(req)} title="Комментарии">
                                                                        <MessageSquare className="h-4 w-4 mr-1.5"/>
                                                                        <span className="text-xs font-semibold">{req.commentCount}</span>
                                                                    </Button>
                                                                    <Button variant="ghost" size="sm" className={cn("px-2", hasNewPhotos ? "text-yellow-600 bg-yellow-100 animate-pulse hover:text-yellow-700 hover:bg-yellow-200" : "hover:text-indigo-700")} onClick={() => openPhotos(req)} title="Фотографии">
                                                                        <Camera className="h-4 w-4 mr-1.5"/>
                                                                        <span className="text-xs font-semibold">{req.photoCount}</span>
                                                                    </Button>                                                                
                                                                    {isContractor && req.status === 'In work' && !archived && (
                                                                        <Button variant="outline" size="sm" className="px-2 hover:text-indigo-700" 
                                                                            onClick={() => { 
                                                                                setTargetRequestId(req.requestID); 
                                                                                setIsCompleteAlertOpen(true);      
                                                                            }} 
                                                                            title="Завершить заявку">
                                                                            Завершить
                                                                        </Button>
                                                                    )}
                                                                    {(isAdmin || isModerator) && req.status !== 'Closed' && (<Button variant="outline" size="icon" className="px-2 hover:text-indigo-700" onClick={() => openEditForm(req)}><Edit className="h-4 w-4" /></Button>)}
                                                                    {(isAdmin || isModerator) && archived && req.status === 'Closed' && (<Button variant="outline" size="icon" className="px-2 hover:text-indigo-700" onClick={() => {setTargetRequestId(req.requestID); setIsRestoreAlertOpen(true);}}><RotateCcw className="h-4 w-4" /></Button>)}
                                                                    {(isAdmin || isModerator) && (<Button variant="destructive" size="icon" className="px-2 hover:text-indigo-700" onClick={() => openDeleteAlert(req)}><Trash2 className="h-4 w-4" /></Button>)}
                                                                </div>
                                                            </TableCell>
                                                        </TableRow>
                                                    );
                                                })}
                                            </TableBody>
                                        </Table>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </>
            )}
            

            <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Вы собираетесь удалить заявку "{currentRequest?.description?.substring(0, 40)}...". Это действие нельзя будет отменить.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter><AlertDialogCancel>Отмена</AlertDialogCancel><AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction></AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={isCompleteAlertOpen} onOpenChange={setIsCompleteAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Завершить заявку?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Вы уверены, что хотите отметить заявку #{targetRequestId} как выполненную? 
                            После этого заявка перейдет в статус "Выполнена".
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={() => { handleComplete(targetRequestId); setIsCompleteAlertOpen(false); }}>
                            Да, завершить
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={isRestoreAlertOpen} onOpenChange={setIsRestoreAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Восстановить заявку?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Вы уверены, что хотите вернуть заявку #{targetRequestId} из архива в активную работу?
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={() => { handleRestore(targetRequestId); setIsRestoreAlertOpen(false); }}>
                            Да, восстановить
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <RequestDetailsModal 
                isOpen={isDetailsOpen} 
                onClose={() => setIsDetailsOpen(false)} 
                request={currentRequest} 
                highlightUpdate={highlightConfig.details} 
                isAdmin={isAdmin || isModerator}
                footerContent={
                    currentRequest ? (
                        <div className="flex justify-end gap-2 w-full">
                            <Button variant="outline" onClick={() => openCommentsFromDetails(currentRequest)}>
                                <MessageSquare className="mr-2 h-4 w-4" /> Комментарии ({currentRequest.commentCount})
                            </Button>
                            <Button variant="outline" onClick={() => openPhotosFromDetails(currentRequest)}>
                                <Camera className="mr-2 h-4 w-4" /> Фото ({currentRequest.photoCount})
                            </Button>
                        </div>
                    ) : null
                }
            />
            <CommentsModal isOpen={isCommentsOpen} onClose={handleCommentsModalClose} request={currentRequest} hasNew={highlightConfig.comments} />
            <PhotosModal isOpen={isPhotosOpen} onClose={handlePhotosModalClose} request={currentRequest} hasNew={highlightConfig.photos} />
                        <div className="mt-6 border-t pt-4">
                <h3 className="text-sm font-semibold text-muted-foreground mb-3 uppercase tracking-wider">
                    Условные обозначения
                </h3>
                
                <div className="flex flex-wrap gap-6 text-sm">
                    {viewMode === 'gantt' ? (
                        <>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded bg-[#22c55e]"></div>
                                <span className="text-gray-700">В работе (в срок)</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded bg-[#3b82f6]"></div>
                                <span className="text-gray-700">Выполнено</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded bg-[#ef4444]"></div>
                                <span className="text-gray-700">Просрочено (активные)</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded bg-[#94a3b8]"></div>
                                <span className="text-gray-700">Закрыто (Архив)</span>
                            </div>

                        </>
                    ) : (
                        <>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded border border-gray-200 bg-white"></div>
                                <span className="text-gray-700">В работе (в срок) / Закрыто</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded border border-blue-200 bg-blue-100"></div>
                                <span className="text-gray-700">Выполнено</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <div className="w-4 h-4 rounded border border-red-200 bg-red-100"></div>
                                <span className="text-gray-700">Просрочено (активные)</span>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </main>
    );
}