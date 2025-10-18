import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getRequests, deleteRequest, createRequest, updateRequest, restoreRequest } from '@/api/requestApi';
import { getShops } from '@/api/shopApi';
import { getWorkCategories } from '@/api/workCategoryApi';
import { getUrgencyCategories } from '@/api/urgencyCategoryApi';
import { getUsers } from '@/api/adminApi';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { PlusCircle, Trash2, Edit, MessageSquare, Camera, Search, XCircle, RotateCcw, Eye, ArrowUpDown } from 'lucide-react'; 
import Pagination from '@/components/Pagination';
import RequestForm from './RequestForm';
import CommentsModal from './CommentsModal';
import PhotosModal from './PhotosModal';
import RequestDetailsModal from './RequestDetailsModal';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName } from '@/lib/displayNames';
import { logger } from '@/lib/logger'; // <-- ДОБАВЬТЕ ЭТОТ ИМПОРТ

const statusDisplayNames = {
    'In work': 'В работе',
    'Done': 'Выполнена',
    'Closed': 'Закрыта'
};

export default function Requests({ archived = false }) {
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

    const searchParamsString = searchParams.toString();


    useEffect(() => {
        if (!searchParams.has('sort')) {
            setSearchParams(prev => {
                prev.set('sort', 'requestID,asc');
                return prev;
            }, { replace: true });
        }
    }, []);

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
                    // Если кликаем по уже отсортированному столбцу (без Shift), меняем направление
                    const direction = currentSortParams[existingSortIndex].split(',')[1];
                    newSort = [`${clickedField},${direction === 'asc' ? 'desc' : 'asc'}`];
                } else {
                    // Просто сортируем по новому столбцу
                    newSort = [`${clickedField},asc`];
                }
            } else { // Если Shift нажат
                newSort = [...currentSortParams];
                if (existingSortIndex > -1) {
                    // Меняем направление у существующего
                    const direction = newSort[existingSortIndex].split(',')[1];
                    newSort[existingSortIndex] = `${clickedField},${direction === 'asc' ? 'desc' : 'asc'}`;
                } else {
                    // Добавляем новый столбец к сортировке
                    newSort.push(`${clickedField},asc`);
                }
            }

            // Удаляем старые параметры сортировки и добавляем новые
            prev.delete('sort');
            newSort.forEach(s => prev.append('sort', s));
            prev.set('page', '0'); // Сбрасываем на первую страницу при смене сортировки
            return prev;
        }, { replace: true });
    };

    const handleRestore = async (requestId) => {
        try {
            await restoreRequest(requestId);
            reloadRequests();
        } catch (err) {
            // vvv ЗАМЕНИТЕ СТАРЫЙ CATCH НА ЭТОТ vvv
            const errorMessage = err.response?.data || 'Не удалось восстановить заявку';
            logger.error('Restore Request Failed', err); // Логируем полный объект ошибки
            setError(errorMessage); // Показываем сообщение пользователю
            // ^^^
        }
    };

    const updateQueryParam = (key, value) => {
        setSearchParams(prev => {
            if (value === 'ALL' || value === '') {
                prev.delete(key);
            } else {
                prev.set(key, value);
            }

            // Сбрасываем страницу на первую ТОЛЬКО если изменен не параметр 'page'
            if (key !== 'page') {
                prev.set('page', '0');
            }

            return prev;
        }, { replace: true });
    };


    const reloadRequests = useCallback(async () => {
        setLoading(true);
        setError(null);
        
        // Создаем новый объект URLSearchParams из строки, чтобы получить актуальные данные
        const currentParams = new URLSearchParams(searchParamsString);
        
        try {
            const params = {
                page: parseInt(currentParams.get('page') || '0', 10),
                archived,
                searchTerm: currentParams.get('searchTerm') || null,
                shopId: currentParams.get('shopId') || null,
                workCategoryId: currentParams.get('workCategoryId') || null,
                urgencyId: currentParams.get('urgencyId') || null,
                contractorId: currentParams.get('contractorId') || null,
                status: currentParams.get('status') || null,
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
    }, [archived, searchParamsString]); // <-- ЗАВИСИМОСТЬ ТЕПЕРЬ СТАБИЛЬНА

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
        // Загрузка данных для фильтров (один раз)
        const fetchFiltersData = async () => {
            try {
                const [shopsRes, workCatsRes, urgencyCatsRes, contractorsRes] = await Promise.all([
                    getShops({ size: 1000 }),
                    getWorkCategories({ size: 1000 }),
                    getUrgencyCategories(),
                    getUsers({ role: 'Contractor', size: 1000 })
                ]);
                setShops(shopsRes.data.content);
                setWorkCategories(workCatsRes.data.content);
                setUrgencyCategories(urgencyCatsRes.data);
                setContractors(contractorsRes.data.content);
            } catch (error) {
                console.error("Failed to fetch filter data", error);
            }
        };
        fetchFiltersData();
    }, []);


    useEffect(() => {
        reloadRequests();
    }, [reloadRequests]);

    const handleFormSubmit = async (formData) => {
        setFormApiError(null);
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
    
    const openDetails = (req) => {
        setCurrentRequest(req);
        setIsDetailsOpen(true);
    };

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
    const sort = searchParams.getAll('sort');

    return (
        <main className="container mx-auto p-6">
            {/* ... */}
            <div className="flex items-center gap-4 mb-4">
                {(sort.length > 1 || (sort.length === 1 && sort[0] !== 'requestID,asc')) && (
                    <Button variant="outline" onClick={handleResetSort}>
                        <XCircle className="mr-2 h-4 w-4" />Сбросить сортировку
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-2 mb-4 p-4 border rounded-lg bg-gray-50 items-center">
                 <Input placeholder="Поиск..." value={searchTerm} onChange={e => updateQueryParam('searchTerm', e.target.value)} className="xl:col-span-2" />
                                <Select onValueChange={(v) => updateQueryParam('shopId', v)} value={shopId}>
                    <SelectTrigger><SelectValue placeholder="Магазин" /></SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">Все магазины</SelectItem>
                        {shops.map(s => <SelectItem key={s.shopID} value={s.shopID.toString()}>{s.shopName}</SelectItem>)}
                    </SelectContent>
                </Select>
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

                <Select onValueChange={(v) => updateQueryParam('contractorId', v)} value={contractorId}>
                    <SelectTrigger><SelectValue placeholder="Исполнитель" /></SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">Все диспетчеры</SelectItem>
                        {contractors.map(c => <SelectItem key={c.userID} value={c.userID.toString()}>{c.login}</SelectItem>)}
                    </SelectContent>
                </Select>
            </div>





            {loading && <p>Загрузка...</p>}
            {error && <p className="text-red-500">{error}</p>}
            
            {!loading && !error && (
                <div className="rounded-md border">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableHeader field="requestID">ID</SortableHeader>
                                <SortableHeader field="description">Описание</SortableHeader>
                                <SortableHeader field="shopName">Магазин</SortableHeader>
                                <SortableHeader field="workCategoryName">Вид работы</SortableHeader>
                                <SortableHeader field="urgencyName">Срочность</SortableHeader>
                                <SortableHeader field="assignedContractorName">Исполнитель</SortableHeader>
                                <SortableHeader field="status">Статус</SortableHeader>
                                <SortableHeader field="daysRemaining">Срок</SortableHeader>
                                <TableHead>Действия</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {requests.map(req => (
                                <TableRow key={req.requestID} className={cn({ 'bg-red-100': req.daysRemaining < 0 })}>
                                    <TableCell>{req.requestID}</TableCell>
                                    <TableCell className="font-medium">
                                        {req.description?.substring(0, 50) + (req.description?.length > 50 ? '...' : '')}
                                    </TableCell>
                                    <TableCell>{req.shopName}</TableCell>
                                    <TableCell>{req.workCategoryName}</TableCell>
                                    <TableCell>{getUrgencyDisplayName(req.urgencyName)}</TableCell>
                                    <TableCell>{req.assignedContractorName || '—'}</TableCell>
                                    <TableCell>{statusDisplayNames[req.status]}</TableCell>
                                    <TableCell className={cn({ 'font-bold text-red-600': req.daysRemaining < 0, 'text-green-600': req.daysRemaining > 0 })}>
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
                                {req.status !== 'Closed' && (
                                    <Button variant="outline" size="icon" onClick={() => openEditForm(req)} title="Редактировать"><Edit className="h-4 w-4" /></Button>
                                )}

                                {archived && req.status === 'Closed' && (
                                    <Button variant="outline" size="icon" onClick={() => handleRestore(req.requestID)} title="Восстановить"><RotateCcw className="h-4 w-4" /></Button>
                                )}

                                <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(req)} title="Удалить"><Trash2 className="h-4 w-4" /></Button>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </div>
            )}
            
            <Pagination 
                currentPage={page}
                totalPages={paginationData.totalPages}
                onPageChange={(p) => updateQueryParam('page', p)}
            />

            <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        {/* vvv ИЗМЕНИТЕ ОПИСАНИЕ УДАЛЕНИЯ vvv */}
                        <AlertDialogDescription>
                            Вы собираетесь удалить заявку "{currentRequest?.description?.substring(0, 40)}...". Это действие нельзя будет отменить.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter><AlertDialogCancel>Отмена</AlertDialogCancel><AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction></AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <RequestDetailsModal isOpen={isDetailsOpen} onClose={() => setIsDetailsOpen(false)} request={currentRequest} />
            <CommentsModal isOpen={isCommentsOpen} onClose={handleCommentsModalClose} request={currentRequest} />
            <PhotosModal isOpen={isPhotosOpen} onClose={handlePhotosModalClose} request={currentRequest} />
        </main>
    );
}