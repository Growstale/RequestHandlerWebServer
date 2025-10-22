import React, { useState, useEffect, useCallback } from 'react'
import { getShopContractorChats, createShopContractorChat, updateShopContractorChat, deleteShopContractorChat } from '@/api/shopContractorChatApi'
import { getShops } from '@/api/shopApi'
import { getContractors } from '@/api/adminApi'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog"
import { ArrowUpDown, PlusCircle, Trash2, Edit, XCircle } from 'lucide-react'
import ShopContractorChatForm from './ShopContractorChatForm'
import Pagination from '@/components/Pagination'
import { cn } from '@/lib/utils'

export default function ShopContractorChats() {
  const [chats, setChats] = useState([])
  const [shops, setShops] = useState([])
  const [contractors, setContractors] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [sortConfig, setSortConfig] = useState([{ field: 'shopContractorChatID', direction: 'asc' }])
  
  const [currentPage, setCurrentPage] = useState(0);
  const [paginationData, setPaginationData] = useState({ totalPages: 0, totalItems: 0 });

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentChat, setCurrentChat] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const reloadChats = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getShopContractorChats({ sortConfig, page: currentPage })
      setChats(response.data.content)
      setPaginationData({ totalPages: response.data.totalPages, totalItems: response.data.totalItems });
    } catch (err) {
      setError(err.response?.data || 'Не удалось обновить список чатов')
    } finally {
      setLoading(false);
    }
  }, [sortConfig, currentPage]);
  
  useEffect(() => {
    const fetchDropdownData = async () => {
        try {
            const [shopsRes, contractorsRes] = await Promise.all([
                getShops({ size: 1000 }),
                getContractors()
            ]);
            setShops(shopsRes.data.content);
            setContractors(contractorsRes.data);
        } catch (err) {
            console.error("Не удалось загрузить данные для форм:", err);
            setError("Ошибка загрузки данных для форм");
        }
    }
    fetchDropdownData();
  }, []); 

  useEffect(() => {
    reloadChats();
  }, [reloadChats]) 

  useEffect(() => {
    setCurrentPage(0);
  }, [sortConfig]);

  const handleSort = (clickedField, e) => {
    const isShiftPressed = e.shiftKey;
    setSortConfig(currentConfig => {
      const existingSortIndex = currentConfig.findIndex(s => s.field === clickedField);
      if (!isShiftPressed) {
        return (existingSortIndex > -1)
          ? [{ field: clickedField, direction: currentConfig[existingSortIndex].direction === 'asc' ? 'desc' : 'asc' }]
          : [{ field: clickedField, direction: 'asc' }];
      }
      let newConfig = [...currentConfig];
      if (existingSortIndex > -1) {
        newConfig[existingSortIndex].direction = newConfig[existingSortIndex].direction === 'asc' ? 'desc' : 'asc';
      } else {
        newConfig.push({ field: clickedField, direction: 'asc' });
      }
      return newConfig;
    });
  };
  
  const handleFormSubmit = async (formData) => {
    setFormApiError(null)
    try {
      if (currentChat) {
        await updateShopContractorChat(currentChat.shopContractorChatID, formData)
      } else {
        await createShopContractorChat(formData)
      }
      setIsFormOpen(false)
      reloadChats() 
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка')
    }
  }

  const handleDeleteConfirm = async () => {
    if (!currentChat) return
    try {
      await deleteShopContractorChat(currentChat.shopContractorChatID)
      setIsAlertOpen(false)
      if (chats.length === 1 && currentPage > 0) {
        setCurrentPage(currentPage - 1);
      } else {
        reloadChats();
      }
    } catch (err) {
      console.error("Ошибка удаления:", err.response?.data)
      setIsAlertOpen(false)
    }
  }

  const SortableHeader = ({ field, children }) => {
    const sortInfo = sortConfig.find(s => s.field === field);
    const sortIndex = sortConfig.findIndex(s => s.field === field);
    const directionIcon = sortInfo ? (sortInfo.direction === 'asc' ? '↓' : '↑') : '';
    
    return (
        <TableHead className="cursor-pointer select-none" onClick={(e) => handleSort(field, e)}>
            <div className={cn("flex items-center gap-2", { "text-blue-600 font-bold": sortInfo })}>
                {children} 
                {sortInfo ? (
                    <span className="flex items-center gap-1">
                        {directionIcon}
                        {sortConfig.length > 1 && <span className="text-xs text-white bg-blue-500 rounded-full w-4 h-4 flex items-center justify-center">{sortIndex + 1}</span>}
                    </span>
                ) : <ArrowUpDown className="h-4 w-4 opacity-30"/>}
            </div>
        </TableHead>
    )
  }

  const openCreateForm = () => { setCurrentChat(null); setFormApiError(null); setIsFormOpen(true); }
  const openEditForm = (chat) => { setCurrentChat(chat); setFormApiError(null); setIsFormOpen(true); }
  const openDeleteAlert = (chat) => { setCurrentChat(chat); setIsAlertOpen(true); }

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Управление чатами</h1>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать связь</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{currentChat ? 'Редактировать связь' : 'Новая связь'}</DialogTitle>
            </DialogHeader>
            <ShopContractorChatForm
              key={currentChat ? currentChat.shopContractorChatID : 'new'}
              currentChat={currentChat}
              shops={shops}
              contractors={contractors}
              onSubmit={handleFormSubmit} 
              onCancel={() => setIsFormOpen(false)}
              apiError={formApiError}
            />
          </DialogContent>
        </Dialog>
      </div>

      <p className="text-sm text-muted-foreground mb-4">Настройте связи между магазинами, подрядчиками и их Telegram-чатами для отправки уведомлений.</p>
      
      {(sortConfig.length > 1 || sortConfig[0].field !== 'shopContractorChatID') && (
        <Button variant="outline" onClick={() => setSortConfig([{ field: 'shopContractorChatID', direction: 'asc' }])} className="mb-4">
          <XCircle className="mr-2 h-4 w-4" />Сбросить сортировку
        </Button>
      )}

      {loading && <p>Загрузка...</p>}
      {error && <p className="text-red-500">{error}</p>}
      
      {!loading && !error && (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <SortableHeader field="shopContractorChatID">ID</SortableHeader>
                <SortableHeader field="shopName">Магазин</SortableHeader>
                <SortableHeader field="contractorLogin">Подрядчик</SortableHeader>
                <SortableHeader field="telegramID">Telegram ID чата</SortableHeader>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {chats.map(chat => (
                <TableRow key={chat.shopContractorChatID}>
                  <TableCell>{chat.shopContractorChatID}</TableCell>
                  <TableCell className="font-medium">{chat.shopName}</TableCell>
                  <TableCell>{chat.contractorLogin}</TableCell>
                  <TableCell>{chat.telegramID}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="icon" onClick={() => openEditForm(chat)}><Edit className="h-4 w-4" /></Button>
                      <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(chat)}><Trash2 className="h-4 w-4" /></Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Pagination currentPage={currentPage} totalPages={paginationData.totalPages} onPageChange={setCurrentPage} />

      <AlertDialog open={isAlertOpen} onOpenChange={setIsAlertOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
            <AlertDialogDescription>Это действие удалит связь чата для магазина "{currentChat?.shopName}" и подрядчика "{currentChat?.contractorLogin}".</AlertDialogDescription>
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