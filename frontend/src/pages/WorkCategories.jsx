import React, { useState, useEffect, useCallback } from 'react'
import { getWorkCategories, createWorkCategory, updateWorkCategory, deleteWorkCategory } from '@/api/workCategoryApi' 
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
import { ArrowUpDown, PlusCircle, Trash2, Edit, XCircle, AlertCircle } from 'lucide-react'
import WorkCategoryForm from './WorkCategoryForm'
import Pagination from '@/components/Pagination'
import { cn } from '@/lib/utils'

export default function WorkCategories() {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [sortConfig, setSortConfig] = useState([{ field: 'workCategoryID', direction: 'asc' }])
  
  const [currentPage, setCurrentPage] = useState(0);
  const [paginationData, setPaginationData] = useState({ totalPages: 0, totalItems: 0 });

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentCategory, setCurrentCategory] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const [deleteError, setDeleteError] = useState(null);

  const reloadCategories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getWorkCategories({ sortConfig, page: currentPage })
      setCategories(response.data.content)
      setPaginationData({
        totalPages: response.data.totalPages,
        totalItems: response.data.totalItems,
      });
    } catch (err) {
      setError(err.response?.data || 'Не удалось обновить список категорий')
    } finally {
      setLoading(false);
    }
  }, [sortConfig, currentPage]);
  
  useEffect(() => {
    reloadCategories();
  }, [reloadCategories]) 

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
      if (currentCategory) {
        await updateWorkCategory(currentCategory.workCategoryID, formData)
      } else {
        await createWorkCategory(formData)
      }
      setIsFormOpen(false)
      reloadCategories() 
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка')
    }
  }

  const handleDeleteConfirm = async () => {
    if (!currentCategory) return
    try {
      await deleteWorkCategory(currentCategory.workCategoryID)
      setIsAlertOpen(false)
      if (categories.length === 1 && currentPage > 0) {
        setCurrentPage(currentPage - 1);
      } else {
        reloadCategories();
      }
    } catch (err) {
      console.error("Ошибка удаления:", err.response?.data);
      setDeleteError(err.response?.data || "Произошла непредвиденная ошибка.");
      setIsAlertOpen(false);
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

  const openCreateForm = () => { setCurrentCategory(null); setFormApiError(null); setIsFormOpen(true); }
  const openEditForm = (category) => { setCurrentCategory(category); setFormApiError(null); setIsFormOpen(true); }
  const openDeleteAlert = (category) => { setCurrentCategory(category); setIsAlertOpen(true); }

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Управление видами работ</h1>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать вид работы</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{currentCategory ? 'Редактировать вид работы' : 'Новый вид работы'}</DialogTitle>
              <DialogDescription>{currentCategory ? `Вы редактируете данные: ${currentCategory.workCategoryName}` : 'Заполните форму для создания нового вида работы.'}</DialogDescription>
            </DialogHeader>
            <WorkCategoryForm 
              key={currentCategory ? currentCategory.workCategoryID : 'new-category'}
              currentCategory={currentCategory}
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
          {(sortConfig.length > 1 || sortConfig[0].field !== 'workCategoryID' || sortConfig[0].direction !== 'asc') && (
            <Button variant="outline" onClick={() => setSortConfig([{ field: 'workCategoryID', direction: 'asc' }])}>
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
                <SortableHeader field="workCategoryID">ID</SortableHeader>
                <SortableHeader field="workCategoryName">Название</SortableHeader>
                <SortableHeader field="requestCount">Заявок</SortableHeader>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map(category => (
                <TableRow key={category.workCategoryID}>
                  <TableCell>{category.workCategoryID}</TableCell>
                  <TableCell className="font-medium">{category.workCategoryName}</TableCell>
                  <TableCell>{category.requestCount}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="icon" onClick={() => openEditForm(category)}><Edit className="h-4 w-4" /></Button>
                                            <Button 
                                                variant="destructive" 
                                                size="icon" 
                                                onClick={() => openDeleteAlert(category)}
                                                disabled={category.requestCount > 0}
                                                title={category.requestCount > 0 ? "Нельзя удалить, т.к. категория используется" : "Удалить"}
                                            >
                                                <Trash2 className="h-4 w-4" />
                                            </Button>
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

                  <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Вы собираетесь удалить категорию <span className="font-bold">{currentCategory?.workCategoryName}</span>. Это действие нельзя будет отменить.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDeleteConfirm}>Удалить</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={!!deleteError} onOpenChange={() => setDeleteError(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle className="flex items-center gap-2">
                            <AlertCircle className="text-destructive" />
                            Ошибка удаления
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            {deleteError}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogAction onClick={() => setDeleteError(null)}>OK</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
    </main>
  )
}