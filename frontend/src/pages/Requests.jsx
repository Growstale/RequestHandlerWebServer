import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getRequests, deleteRequest, createRequest, updateRequest, restoreRequest, completeRequest } from '@/api/requestApi';
import { getShops } from '@/api/shopApi';
import { getWorkCategories } from '@/api/workCategoryApi';
import { getUrgencyCategories } from '@/api/urgencyCategoryApi';
import { getUsers, getContractors } from '@/api/adminApi';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { BarChart3, List, PlusCircle, Trash2, Edit, MessageSquare, Camera, Search, XCircle, RotateCcw, Eye, ArrowUpDown, Store } from 'lucide-react';
import Pagination from '@/components/Pagination';
import RequestForm from './RequestForm';
import CommentsModal from './CommentsModal';
import PhotosModal from './PhotosModal';
import RequestDetailsModal from './RequestDetailsModal';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName, getStatusDisplayName } from '@/lib/displayNames'; 
import { logger } from '@/lib/logger';
import { useAuth } from '@/context/AuthProvider'; 
import GanttChartView from './GanttChartView';

const filterKeys = ['searchTerm', 'shopId', 'workCategoryId', 'urgencyId', 'contractorId', 'status', 'overdue'];

export default function Requests({ archived = false }) {
    const { user } = useAuth();
    const isAdmin = user?.role === 'RetailAdmin';
    const isContractor = user?.role === 'Contractor';
    const isStoreManager = user?.role === 'StoreManager';
    const [viewMode, setViewMode] = useState('table'); 

    const [requests, setRequests] = useState([]);
    const [groupedRequests, setGroupedRequests] = useState({});
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

    const searchParamsString = searchParams.toString();

    const areFiltersActive = filterKeys.some(key => searchParams.has(key));
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        if (viewMode === 'byShop' && requests.length > 0) {
            const grouped = requests.reduce((acc, req) => {
                const shopName = req.shopName || 'Без магазина';
                if (!acc[shopName]) {
                    acc[shopName] = [];
                }
                acc[shopName].push(req);
                return acc;
            }, {});
            setGroupedRequests(grouped);
        }
    }, [viewMode, requests]);

    useEffect(() => {
        if (!searchParams.has('sort')) {
            setSearchParams(prev => {
                prev.set('sort', 'requestID,asc');
                return prev;
            }, { replace: true });
        }
    }, []);

    const handleResetFilters = () => {
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
        sortConfig: (searchParams.getAll('sort').length > 0 ? searchParams.getAll('sort') : ['requestID,asc']).map(s => ({
            field: s.split(',')[0],
            direction: s.split(',')[1] || 'asc'
        }))
    }), [archived, searchParamsString]);

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
            reloadRequests();
        } catch (err) {
            const errorMessage = err.response?.data || 'Не удалось восстановить заявку';
            logger.error('Restore Request Failed', err);
            setError(errorMessage);
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
            reloadRequests();
        } catch (err) {
            const errorMessage = err.response?.data || 'Не удалось завершить заявку';
            logger.error('Complete Request Failed', err);
            setError(errorMessage);
        }
    };

    const reloadRequests = useCallback(async () => {
        setLoading(true);
        setError(null);
        
        const currentParams = new URLSearchParams(searchParamsString);
        
        try {
            const isByShopView = currentParams.get('view') === 'byShop';
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
                sortConfig: (currentParams.getAll('sort').length > 0 ? currentParams.getAll('sort') : ['requestID,asc']).map(s => ({
                    field: s.split(',')[0],
                    direction: s.split(',')[1] || 'asc'
                }))
            };
            
            const response = await getRequests(params);
            setRequests(response.data.content);
            setPaginationData({ totalPages: response.data.totalPages, totalItems: response.data.totalItems });
        } catch (err) {
            setError(err.response?.data || `Не удалось загрузить ${archived ? 'архив' : 'заявки'}`);
        } finally {
            setLoading(false);
        }
    }, [archived, searchParamsString]);

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

                if (isAdmin) {
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
    }, [user, isAdmin]); 


    useEffect(() => {
        reloadRequests();
    }, [reloadRequests]);

    const handleFormSubmit = async (formData) => {
        setFormApiError(null);
        setIsSubmitting(true);
        try {
            if (currentRequest) {
                await updateRequest(currentRequest.requestID, formData);
            } else {
                await createRequest(formData);
            }
            setIsFormOpen(false);
            reloadRequests();
        } catch (err) {
            console.error("Ошибка при отправке формы заявки:", err.response || err);
            setFormApiError(err.response?.data || 'Произошла ошибка. Проверьте консоль для деталей.');
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDeleteConfirm = async () => {
        if (!currentRequest) return;
        try {
            await deleteRequest(currentRequest.requestID);
            setIsAlertOpen(false);
            reloadRequests();
        } catch (err) {
            console.error("Ошибка удаления:", err.response?.data);
            setIsAlertOpen(false);
        }
    };
    
    const openDetails = useCallback((req) => {
        setCurrentRequest(req);
        setIsDetailsOpen(true);
    }, []);

    const openCreateForm = () => { setCurrentRequest(null); setFormApiError(null); setIsFormOpen(true); };
    const openEditForm = (req) => { setCurrentRequest(req); setFormApiError(null); setIsFormOpen(true); };
    const openDeleteAlert = (req) => { setCurrentRequest(req); setIsAlertOpen(true); };
    const openComments = (req) => { setCurrentRequest(req); setIsCommentsOpen(true); };
    const openPhotos = (req) => { setCurrentRequest(req); setIsPhotosOpen(true); };

    const handleCommentsModalClose = () => {
        setIsCommentsOpen(false);
        reloadRequests();
    };

    const handlePhotosModalClose = () => {
        setIsPhotosOpen(false);
        reloadRequests();
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
            <div className="flex justify-between items-center mb-4">
                <h1 className="text-3xl font-semibold">{archived ? 'Архив заявок' : 'Управление заявками'}</h1>
                
                <div className="flex items-center gap-2">
                    {!archived && (
                        <>
                            <Button variant={viewMode === 'table' ? 'secondary' : 'outline'} onClick={() => setViewMode('table')}>
                                <List className="mr-2 h-4 w-4" /> Таблица
                            </Button>
                            <Button variant={viewMode === 'gantt' ? 'secondary' : 'outline'} onClick={() => setViewMode('gantt')}>
                                <BarChart3 className="mr-2 h-4 w-4" /> Диаграмма
                            </Button>
                            <Button variant={viewMode === 'byShop' ? 'secondary' : 'outline'} onClick={() => setViewMode('byShop')}>
                                <Store className="mr-2 h-4 w-4" /> По магазинам
                            </Button>
                        </>
                    )}


                    {isAdmin && !archived && (
                        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
                            <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать заявку</Button></DialogTrigger>
                            <DialogContent className="max-w-3xl">
                                <DialogHeader>
                                    <DialogTitle>{currentRequest ? 'Редактировать заявку' : 'Новая заявка'}</DialogTitle>
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
                    )}
                </div>

            </div>

            <p className="text-sm text-muted-foreground mb-4">Кликните на заголовок для сортировки. Удерживайте <strong>Shift</strong> для сортировки по нескольким столбцам.</p>

            <div className="flex items-center gap-4 mb-4">
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

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-2 mb-4 p-4 border rounded-lg bg-gray-50 items-center">
                 <Input placeholder="Поиск..." value={searchTerm} onChange={e => updateQueryParam('searchTerm', e.target.value)} className="xl:col-span-2" />
                 {isAdmin && (
                    <Select onValueChange={(v) => updateQueryParam('shopId', v)} value={shopId}>
                        <SelectTrigger><SelectValue placeholder="Магазин" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Все магазины</SelectItem>
                            {shops.map(s => <SelectItem key={s.shopID} value={s.shopID.toString()}>{s.shopName}</SelectItem>)}
                        </SelectContent>
                    </Select>
                 )}

                <Select onValueChange={(v) => updateQueryParam('workCategoryId', v)} value={workCategoryId}>
                    <SelectTrigger><SelectValue placeholder="Вид работы" /></SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">Все виды</SelectItem>
                        {workCategories.map(wc => <SelectItem key={wc.workCategoryID} value={wc.workCategoryID.toString()}>{wc.workCategoryName}</SelectItem>)}
                    </SelectContent>
                </Select>
                <Select onValueChange={(v) => updateQueryParam('urgencyId', v)} value={urgencyId}>
                    <SelectTrigger><SelectValue placeholder="Срочность" /></SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">Вся срочность</SelectItem>
                        {urgencyCategories.map(uc => <SelectItem key={uc.urgencyID} value={uc.urgencyID.toString()}>{getUrgencyDisplayName(uc.urgencyName)}</SelectItem>)}
                    </SelectContent>
                </Select>
                                {!archived && (
                    <Select onValueChange={(v) => updateQueryParam('status', v)} value={status}>
                        <SelectTrigger><SelectValue placeholder="Статус" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Все статусы</SelectItem>
                            <SelectItem value="In work">В работе</SelectItem>
                            <SelectItem value="Done">Выполнена</SelectItem>
                        </SelectContent>
                    </Select>
                )}

                {isAdmin && (
                    <Select onValueChange={(v) => updateQueryParam('contractorId', v)} value={contractorId}>
                        <SelectTrigger><SelectValue placeholder="Диспетчер" /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="ALL">Все диспетчеры</SelectItem>
                            {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                        </SelectContent>
                    </Select>
                )}
                {!archived && (
                    <div className="flex items-center space-x-2 p-2 rounded-md justify-start xl:justify-center">
                        <input
                            type="checkbox"
                            id="overdue-filter"
                            checked={overdue}
                            onChange={e => updateQueryParam('overdue', e.target.checked)}
                            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
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
                                <SortableHeader field="requestID">ID</SortableHeader>
                                <SortableHeader field="description">Описание</SortableHeader>
                                <SortableHeader field="shopName">Магазин</SortableHeader>
                                <SortableHeader field="workCategoryName">Вид работы</SortableHeader>
                                <SortableHeader field="urgencyName">Срочность</SortableHeader>
                                <SortableHeader field="assignedContractorName">Диспетчер</SortableHeader>
                                <SortableHeader field="status">Статус</SortableHeader>
                                <SortableHeader field="daysRemaining">Срок</SortableHeader>
                                <TableHead>Действия</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {requests.map(req => (
                                <TableRow key={req.requestID} className={cn({ 
                                    'bg-red-100': req.isOverdue && req.status === 'In work',
                                    'bg-blue-100': req.status === 'Done' 
                                })}>
                                    <TableCell>{req.requestID}</TableCell>
                                    <TableCell className="font-medium">
                                        {req.description?.length > 50 
                                            ? req.description.substring(0, 50) + '...' 
                                            : req.description
                                        }
                                    </TableCell>
                                    <TableCell>{req.shopName}</TableCell>
                                    <TableCell>{req.workCategoryName}</TableCell>
                                    <TableCell>{getUrgencyDisplayName(req.urgencyName)}</TableCell>
                                    <TableCell>{req.assignedContractorName || '—'}</TableCell>
                                    <TableCell>{getStatusDisplayName(req.status)}</TableCell>
                                    <TableCell className={cn({ 'font-bold text-red-600': req.isOverdue, 'text-green-600': req.daysRemaining > 0 })}>
                                        {req.daysRemaining !== null ? req.daysRemaining : '—'}
                                    </TableCell>
                                    <TableCell>
                                        <div className="flex gap-1">
                                            <Button variant="ghost" size="icon" onClick={() => openDetails(req)} title="Просмотр деталей">
                                                <Eye className="h-4 w-4"/>
                                            </Button>
                                            <Button variant="ghost" size="icon" onClick={() => openComments(req)}>
                                                <MessageSquare className="h-4 w-4"/>
                                                <span className="text-xs ml-1">{req.commentCount}</span>
                                            </Button>
                                            <Button variant="ghost" size="icon" onClick={() => openPhotos(req)}>
                                                <Camera className="h-4 w-4"/>
                                                <span className="text-xs ml-1">{req.photoCount}</span>
                                            </Button>

                                                                            {isContractor && req.status === 'In work' && !archived && (
                                                <Button variant="outline" size="sm" onClick={() => handleComplete(req.requestID)} title="Завершить заявку">
                                                    Завершить
                                                </Button>
                                            )}

                                            {isAdmin && req.status !== 'Closed' && (
                                                <Button variant="outline" size="icon" onClick={() => openEditForm(req)} title="Редактировать"><Edit className="h-4 w-4" /></Button>
                                            )}

                                            {isAdmin && archived && req.status === 'Closed' && (
                                                <Button variant="outline" size="icon" onClick={() => handleRestore(req.requestID)} title="Восстановить"><RotateCcw className="h-4 w-4" /></Button>
                                            )}
                                            
                                            {isAdmin && (
                                                <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(req)} title="Удалить"><Trash2 className="h-4 w-4" /></Button>
                                            )}
                                        </div>
                                    </TableCell>

                                </TableRow>
                            ))}
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
                        <GanttChartView 
                            filters={currentFilters} 
                            onTaskClick={openDetails}
                        />
                    )}

                                        {viewMode === 'byShop' && (
                        <div className="space-y-8">
                            {Object.keys(groupedRequests).sort().map(shopName => (
                                <div key={shopName}>
                                    <h2 className="text-xl font-bold mb-2 border-b pb-2">{shopName}</h2>
                                    <div className="rounded-md border">
                                        <Table>
                                            <TableHeader>
                                                {/* Копируем заголовок из основного вида, но без магазина */}
                                                <TableRow>
                                                    <SortableHeader field="requestID">ID</SortableHeader>
                                                    <SortableHeader field="description">Описание</SortableHeader>
                                                    <SortableHeader field="workCategoryName">Вид работы</SortableHeader>
                                                    <SortableHeader field="urgencyName">Срочность</SortableHeader>
                                                    <SortableHeader field="assignedContractorName">Диспетчер</SortableHeader>
                                                    <SortableHeader field="status">Статус</SortableHeader>
                                                    <SortableHeader field="daysRemaining">Срок</SortableHeader>
                                                    <TableHead>Действия</TableHead>
                                                </TableRow>
                                            </TableHeader>
                                            <TableBody>
                                                {groupedRequests[shopName].map(req => (
                                                    <TableRow key={req.requestID} className={cn({ 
                                                        'bg-red-100': req.isOverdue && req.status === 'In work',
                                                        'bg-blue-100': req.status === 'Done' 
                                                    })}>
                                                        {/* Копируем ячейки, но без магазина */}
                                                        <TableCell>{req.requestID}</TableCell>
                                                        <TableCell className="font-medium">{req.description?.substring(0, 50)}...</TableCell>
                                                        <TableCell>{req.workCategoryName}</TableCell>
                                                        <TableCell>{getUrgencyDisplayName(req.urgencyName)}</TableCell>
                                                        <TableCell>{req.assignedContractorName || '—'}</TableCell>
                                                        <TableCell>{getStatusDisplayName(req.status)}</TableCell>
                                                        <TableCell className={cn({ 'font-bold text-red-600': req.isOverdue, 'text-green-600': req.daysRemaining > 0 })}>{req.daysRemaining !== null ? req.daysRemaining : '—'}</TableCell>
                                                        <TableCell>
                                                            {/* Копируем блок с кнопками действий */}
                                                            <div className="flex gap-1">
                                                                <Button variant="ghost" size="icon" onClick={() => openDetails(req)} title="Просмотр деталей"><Eye className="h-4 w-4"/></Button>
                                                                <Button variant="ghost" size="icon" onClick={() => openComments(req)}><MessageSquare className="h-4 w-4"/><span className="text-xs ml-1">{req.commentCount}</span></Button>
                                                                <Button variant="ghost" size="icon" onClick={() => openPhotos(req)}><Camera className="h-4 w-4"/><span className="text-xs ml-1">{req.photoCount}</span></Button>
                                                                {isContractor && req.status === 'In work' && !archived && (<Button variant="outline" size="sm" onClick={() => handleComplete(req.requestID)}>Завершить</Button>)}
                                                                {isAdmin && req.status !== 'Closed' && (<Button variant="outline" size="icon" onClick={() => openEditForm(req)}><Edit className="h-4 w-4" /></Button>)}
                                                                {isAdmin && archived && req.status === 'Closed' && (<Button variant="outline" size="icon" onClick={() => handleRestore(req.requestID)}><RotateCcw className="h-4 w-4" /></Button>)}
                                                                {isAdmin && (<Button variant="destructive" size="icon" onClick={() => openDeleteAlert(req)}><Trash2 className="h-4 w-4" /></Button>)}
                                                            </div>
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
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

            <RequestDetailsModal 
                isOpen={isDetailsOpen} 
                onClose={() => setIsDetailsOpen(false)} 
                request={currentRequest} 
                footerContent={
                    viewMode === 'gantt' && currentRequest ? (
                        <div className="flex justify-end gap-2 w-full">
                            <Button variant="outline" onClick={() => {
                                setIsDetailsOpen(false);
                                openComments(currentRequest);
                            }}>
                                <MessageSquare className="mr-2 h-4 w-4" /> Комментарии ({currentRequest.commentCount})
                            </Button>
                            <Button variant="outline" onClick={() => {
                                setIsDetailsOpen(false);
                                openPhotos(currentRequest);
                            }}>
                                <Camera className="mr-2 h-4 w-4" /> Фото ({currentRequest.photoCount})
                            </Button>
                        </div>
                    ) : null
                }
            />
            <CommentsModal isOpen={isCommentsOpen} onClose={handleCommentsModalClose} request={currentRequest} />
            <PhotosModal isOpen={isPhotosOpen} onClose={handlePhotosModalClose} request={currentRequest} />
        </main>
    );
}