import React, { useState, useEffect, useCallback } from 'react'
import { getShops, createShop, updateShop, deleteShop } from '@/api/shopApi' 
import { getUsers } from '@/api/adminApi'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger
} from "@/components/ui/dialog"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle
} from "@/components/ui/alert-dialog"
// ВАЖНО: Добавлен AlertCircle
import { ArrowUpDown, PlusCircle, Trash2, Edit, XCircle, AlertCircle } from 'lucide-react'
import ShopForm from './ShopForm'
import Pagination from '@/components/Pagination'
import { cn } from '@/lib/utils'

export default function Shops() {
  const [shops, setShops] = useState([])
  const [storeManagers, setStoreManagers] = useState([]) 
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [sortConfig, setSortConfig] = useState([{ field: 'shopID', direction: 'asc' }])
  
  const [currentPage, setCurrentPage] = useState(0);
  const [paginationData, setPaginationData] = useState({ totalPages: 0, totalItems: 0 });

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentShop, setCurrentShop] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  // 1. Добавляем состояние для ошибки удаления
  const [deleteError, setDeleteError] = useState(null);

  const reloadShops = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getShops({ sortConfig, page: currentPage })
      setShops(response.data.content)
      setPaginationData({
        totalPages: response.data.totalPages,
        totalItems: response.data.totalItems,
      });
    } catch (err) {
      setError(err.response?.data || 'Не удалось обновить список магазинов')
    } finally {
      setLoading(false);
    }
  }, [sortConfig, currentPage]);
  
  useEffect(() => {
    const fetchStoreManagers = async () => {
        try {
            const response = await getUsers({ role: 'StoreManager', size: 1000 });
            if (response.data && Array.isArray(response.data.content)) {
                setStoreManagers(response.data.content);
            }
        } catch (err) {
            console.error("Не удалось загрузить список менеджеров:", err);
        }
    }
    fetchStoreManagers();
  }, []); 

  useEffect(() => {
    reloadShops();
  }, [reloadShops]) 

  useEffect(() => {
    setCurrentPage(0);
  }, [sortConfig]);

  const handleSort = (clickedField, e) => {
    const isShiftPressed = e.shiftKey;

    setSortConfig(currentConfig => {
      const existingSortIndex = currentConfig.findIndex(s => s.field === clickedField);

      if (!isShiftPressed) {
        if (existingSortIndex > -1 && currentConfig.length === 1) {
          return [{ field: clickedField, direction: currentConfig[0].direction === 'asc' ? 'desc' : 'asc' }];
        }
        return [{ field: clickedField, direction: 'asc' }];
      }

      if (existingSortIndex > -1) {
        return currentConfig.map(sortItem => {
          if (sortItem.field === clickedField) {
            return { ...sortItem, direction: sortItem.direction === 'asc' ? 'desc' : 'asc' };
          }
          return sortItem;
        });
      } else {
        return [...currentConfig, { field: clickedField, direction: 'asc' }];
      }
    });
  };
  
  const handleFormSubmit = async (formData) => {
    setFormApiError(null)
    try {
      if (currentShop) {
        await updateShop(currentShop.shopID, formData)
      } else {
        await createShop(formData)
      }
      setIsFormOpen(false)
      reloadShops() 
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка')
    }
  }

  // 2. Исправленная функция удаления
  const handleDeleteConfirm = async () => {
    if (!currentShop) return
    setDeleteError(null); // Сброс ошибки
    
    try {
      await deleteShop(currentShop.shopID)
      
      // Успех -> закрываем окно и обновляем
      setIsAlertOpen(false)
      
      // Логика пагинации (если удалили последний элемент на странице)
      if (shops.length === 1 && currentPage > 0) {
        setCurrentPage(currentPage - 1);
      } else {
        reloadShops();
      }
    } catch (err) {
      // Ошибка -> НЕ закрываем окно, показываем текст
      const resData = err.response?.data;
      let errorMessage = "Не удалось удалить магазин.";
      
      if (typeof resData === 'string') {
          errorMessage = resData;
      } else if (resData && typeof resData === 'object' && resData.message) {
          errorMessage = resData.message;
      }
      
      setDeleteError(errorMessage);
    }
  }

  const SortableHeader = ({ field, children }) => {
    const sortInfo = sortConfig.find(s => s.field === field);
    const sortIndex = sortConfig.findIndex(s => s.field === field);
    const directionIcon = sortInfo ? (sortInfo.direction === 'asc' ? '↓' : '↑') : '';
    
    return (
        <TableHead 
            className="cursor-pointer select-none transition-colors hover:bg-gray-100" 
            onClick={(e) => handleSort(field, e)}
        >
            <div className={cn("flex items-center gap-2", { "text-blue-600 font-bold": sortInfo })}>
                {children} 
                {sortInfo ? (
                    <span className="flex items-center gap-1">
                        {directionIcon}
                        {sortConfig.length > 1 && (
                            <span className="text-xs font-semibold text-white bg-blue-500 rounded-full w-4 h-4 flex items-center justify-center">
                                {sortIndex + 1}
                            </span>
                        )}
                    </span>
                ) : ( <ArrowUpDown className="h-4 w-4 opacity-30"/> )}
            </div>
        </TableHead>
    )
  }

  const openCreateForm = () => { setCurrentShop(null); setFormApiError(null); setIsFormOpen(true); }
  const openEditForm = (shop) => { setCurrentShop(shop); setFormApiError(null); setIsFormOpen(true); }
  const openDeleteAlert = (shop) => { 
      setCurrentShop(shop); 
      setDeleteError(null); // Сброс ошибки при открытии
      setIsAlertOpen(true); 
  }

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Управление магазинами</h1>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать магазин</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{currentShop ? 'Редактировать магазин' : 'Новый магазин'}</DialogTitle>
              <DialogDescription>{currentShop ? `Вы редактируете данные ${currentShop.shopName}` : 'Заполните форму для создания нового магазина.'}</DialogDescription>
            </DialogHeader>
            <ShopForm 
              key={currentShop ? currentShop.shopID : 'new-shop'}
              currentShop={currentShop}
              users={storeManagers}
              onSubmit={handleFormSubmit} 
              onCancel={() => setIsFormOpen(false)}
              apiError={formApiError}
            />
          </DialogContent>
        </Dialog>
      </div>

      <p className="text-sm text-muted-foreground mb-4">Кликните на заголовок для сортировки. Удерживайте <strong>Shift</strong> для сортировки по нескольким столбцам.</p>
      
      <div className="flex items-center justify-between gap-4 mb-4">
        <div className="flex items-center gap-4">
          {(sortConfig.length > 1 || sortConfig[0].field !== 'shopID' || sortConfig[0].direction !== 'asc') && (
            <Button variant="outline" onClick={() => setSortConfig([{ field: 'shopID', direction: 'asc' }])}>
              <XCircle className="mr-2 h-4 w-4" />Сбросить сортировку
            </Button>
          )}
        </div>
        <div className="text-sm text-muted-foreground">
          Найдено: {paginationData.totalItems}
        </div>
      </div>

      {loading && <p>Загрузка...</p>}
      {error && <p className="text-red-500">{error}</p>}
      
      {!loading && !error && (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableHeader field="shopID">ID</SortableHeader>
                <SortableHeader field="shopName">Название</SortableHeader>
                <SortableHeader field="address">Адрес</SortableHeader>
                <SortableHeader field="userLogin">Ответственный</SortableHeader>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {shops.map(shop => (
                <TableRow key={shop.shopID}>
                  <TableCell>{shop.shopID}</TableCell>
                  <TableCell className="font-medium">{shop.shopName}</TableCell>
                  <TableCell>{shop.address || '—'}</TableCell>
                  <TableCell>{shop.userLogin || '—'}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="icon" onClick={() => openEditForm(shop)}><Edit className="h-4 w-4" /></Button>
                      <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(shop)}><Trash2 className="h-4 w-4" /></Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Pagination 
        currentPage={currentPage}
        totalPages={paginationData.totalPages}
        onPageChange={setCurrentPage}
      />

      {/* 3. Обновленное модальное окно удаления */}
      <AlertDialog open={isAlertOpen} onOpenChange={(val) => { 
          if (!val) setDeleteError(null); 
          setIsAlertOpen(val); 
      }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
            <AlertDialogDescription>
                Вы собираетесь удалить магазин <span className="font-bold">{currentShop?.shopName}</span>. Это действие нельзя будет отменить.
            </AlertDialogDescription>
            
            {/* Блок ошибки */}
            {deleteError && (
                <div className="bg-red-50 border border-red-200 text-red-600 p-3 rounded-md mt-2 flex items-start gap-2 text-sm">
                    <AlertCircle className="h-5 w-5 shrink-0 mt-0.5" />
                    <span>{deleteError}</span>
                </div>
            )}
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            {/* 4. Замена AlertDialogAction на Button */}
            <Button 
                variant="destructive" 
                onClick={(e) => {
                    e.preventDefault();
                    handleDeleteConfirm();
                }}
            >
                Удалить
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  )
}