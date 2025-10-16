import React, { useState, useEffect, useMemo, useCallback } from 'react'
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
import { ArrowUpDown, PlusCircle, Trash2, Edit, XCircle } from 'lucide-react'
import ShopForm from './ShopForm'
import { cn } from '@/lib/utils'

export default function Shops() {
  const [shops, setShops] = useState([])
  const [users, setUsers] = useState([]) 
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [sortConfig, setSortConfig] = useState([{ field: 'shopID', direction: 'asc' }])
  
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentShop, setCurrentShop] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const storeManagers = useMemo(() => {
    return users.filter(user => user.roleName === 'StoreManager')
  }, [users])

    const displayedShops = useMemo(() => {
    return shops;
  }, [shops]);

  const reloadShops = useCallback(async () => {
    setLoading(true);
    try {
      const shopsResponse = await getShops(sortConfig)
      setShops(shopsResponse.data)
    } catch (err) {
      setError(err.response?.data || 'Не удалось обновить список магазинов')
    } finally {
      setLoading(false);
    }
  }, [sortConfig]);
  
  useEffect(() => {
    const fetchAndSetData = async () => {
        setLoading(true)
        setError(null)
        try {
            const usersResponse = await getUsers();
            setUsers(usersResponse.data);
            await reloadShops(); 
        } catch (err) {
            setError(err.response?.data || 'Не удалось загрузить данные')
        } finally {
            setLoading(false)
        }
    }
    
    if (users.length === 0) {
      fetchAndSetData();
    } else {
      reloadShops();
    }
  }, [sortConfig, reloadShops]) 


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

  const handleDeleteConfirm = async () => {
    if (!currentShop) return
    try {
      await deleteShop(currentShop.shopID)
      setIsAlertOpen(false)
      reloadShops()
    } catch (err) {
      console.error("Ошибка удаления:", err.response?.data)
      setIsAlertOpen(false)
    }
  }

  const SortableHeader = ({ field, children }) => {
    const sortInfo = sortConfig.find(s => s.field === field);
    const sortIndex = sortConfig.findIndex(s => s.field === field);
    const directionIcon = sortInfo ? (sortInfo.direction === 'asc' ? '↑' : '↓') : '';
    
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
  const openDeleteAlert = (shop) => { setCurrentShop(shop); setIsAlertOpen(true); }

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
      
      <div className="flex items-center gap-4 mb-4">
        {(sortConfig.length > 1 || sortConfig[0].field !== 'shopID' || sortConfig[0].direction !== 'asc') && (
          <Button variant="outline" onClick={() => setSortConfig([{ field: 'shopID', direction: 'asc' }])}>
            <XCircle className="mr-2 h-4 w-4" />Сбросить сортировку
          </Button>
        )}
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
                <SortableHeader field="userLogin">Пользователь</SortableHeader>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayedShops.map(shop => (
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

      <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
            <AlertDialogDescription>Вы собираетесь удалить магазин <span className="font-bold">{currentShop?.shopName}</span>. Это действие нельзя будет отменить.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </main>
  )
}