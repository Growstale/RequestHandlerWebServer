import React, { useState, useEffect, useMemo, useCallback } from 'react'
import { getUsers, createUser, updateUser, deleteUser, getRoles } from '@/api/adminApi'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue
} from '@/components/ui/select'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger
} from "@/components/ui/dialog"
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { ArrowUpDown, PlusCircle, Trash2, Edit, XCircle } from 'lucide-react'
import UserForm from './UserForm'
import { cn } from '@/lib/utils'
import { getRoleDisplayName } from '@/lib/displayNames'

export default function Users() {
  const [allUsers, setAllUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  
  const [filterRole, setFilterRole] = useState('Все')
  const [sortConfig, setSortConfig] = useState([{ field: 'userID', direction: 'asc' }])
  
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isAlertOpen, setIsAlertOpen] = useState(false)
  const [currentUser, setCurrentUser] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const reloadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const usersResponse = await getUsers({ role: filterRole, sortConfig });
      setAllUsers(usersResponse.data);
    } catch (err) {
      setError(err.response?.data || 'Не удалось обновить список пользователей');
    } finally {
      setLoading(false);
    }
  }, [filterRole, sortConfig]);

  useEffect(() => {
    const fetchInitialData = async () => {
      setLoading(true);
      setError(null);
      try {
        const rolesResponse = await getRoles();
        setRoles(rolesResponse.data);
        // Пользователи будут загружены следующим эффектом
      } catch (err) {
        setError(err.response?.data || 'Не удалось загрузить роли');
        setLoading(false);
      }
    };
    fetchInitialData();
  }, []);

  useEffect(() => {
    if (roles.length > 0) {
        reloadUsers();
    }
  }, [roles, reloadUsers]);

  const displayedUsers = useMemo(() => {
    return allUsers;
  }, [allUsers]);

  const handleSort = (clickedField, e) => {
    const isShiftPressed = e.shiftKey;
    setSortConfig(currentConfig => {
      const existingSortIndex = currentConfig.findIndex(s => s.field === clickedField);

      if (!isShiftPressed) {
        if (existingSortIndex === 0 && currentConfig.length === 1) {
          return [{ field: clickedField, direction: currentConfig[0].direction === 'asc' ? 'desc' : 'asc' }];
        }
        return [{ field: clickedField, direction: 'asc' }];
      }

      const newConfig = [...currentConfig];
      if (existingSortIndex > -1) {
        if (newConfig[existingSortIndex].direction === 'asc') {
          newConfig[existingSortIndex].direction = 'desc';
        } else {
          newConfig.splice(existingSortIndex, 1);
        }
      } else {
        newConfig.push({ field: clickedField, direction: 'asc' });
      }

      return newConfig.length > 0 ? newConfig : [{ field: 'userID', direction: 'asc' }];
    });
  };
  
  const handleFormSubmit = async (formData) => {
    setFormApiError(null)
    try {
      if (currentUser) {
        await updateUser(currentUser.userID, formData)
      } else {
        await createUser(formData)
      }
      setIsFormOpen(false)
      reloadUsers();
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка')
    }
  }

  const handleDeleteConfirm = async () => {
    if (!currentUser) return
    try {
      await deleteUser(currentUser.userID)
      setIsAlertOpen(false)
      reloadUsers();
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

  const openCreateForm = () => { setCurrentUser(null); setFormApiError(null); setIsFormOpen(true); };
  const openEditForm = (user) => { setCurrentUser(user); setFormApiError(null); setIsFormOpen(true); };
  const openDeleteAlert = (user) => { setCurrentUser(user); setIsAlertOpen(true); };

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Управление пользователями</h1>
        <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
          <DialogTrigger asChild><Button onClick={openCreateForm}><PlusCircle className="mr-2 h-4 w-4" /> Создать пользователя</Button></DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{currentUser ? 'Редактировать пользователя' : 'Новый пользователь'}</DialogTitle>
              <DialogDescription>{currentUser ? `Вы редактируете данные ${currentUser.login}` : 'Заполните форму для создания нового пользователя.'}</DialogDescription>
            </DialogHeader>
            <UserForm 
              key={currentUser ? currentUser.userID : 'new-user'}
              currentUser={currentUser} 
              onSubmit={handleFormSubmit} 
              onCancel={() => setIsFormOpen(false)}
              apiError={formApiError}
              roles={roles}
            />
          </DialogContent>
        </Dialog>
      </div>
      
      <p className="text-sm text-muted-foreground mb-4">Кликните на заголовок для сортировки. Удерживайте <strong>Shift</strong> для сортировки по нескольким столбцам.</p>

      <div className="flex items-center gap-4 mb-4">
        <Select value={filterRole} onValueChange={setFilterRole}>
          <SelectTrigger className="w-[180px]"><SelectValue placeholder="Фильтр по роли" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="Все">Все</SelectItem>
            {roles.map(role => <SelectItem key={role.roleID} value={role.roleName}>{getRoleDisplayName(role.roleName)}</SelectItem>)}
          </SelectContent>
        </Select>
        {(sortConfig.length > 1 || sortConfig[0].field !== 'userID' || sortConfig[0].direction !== 'asc') && (
          <Button variant="outline" onClick={() => setSortConfig([{ field: 'userID', direction: 'asc' }])}>
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
                <SortableHeader field="userID">ID</SortableHeader>
                <SortableHeader field="login">Логин</SortableHeader>
                <SortableHeader field="fullName">ФИО</SortableHeader>
                <SortableHeader field="roleName">Роль</SortableHeader>
                <SortableHeader field="contactInfo">Контакт. инфо</SortableHeader>
                <SortableHeader field="telegramID">Telegram ID</SortableHeader>
                <TableHead>Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayedUsers.map(user => (
                <TableRow key={user.userID}>
                  <TableCell>{user.userID}</TableCell>
                  <TableCell className="font-medium">{user.login}</TableCell>
                  <TableCell>{user.fullName || '—'}</TableCell>
                  <TableCell>{getRoleDisplayName(user.roleName)}</TableCell>
                  <TableCell>{user.contactInfo || '—'}</TableCell>
                  <TableCell>{user.telegramID || '—'}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="outline" size="icon" onClick={() => openEditForm(user)}><Edit className="h-4 w-4" /></Button>
                      <Button variant="destructive" size="icon" onClick={() => openDeleteAlert(user)}><Trash2 className="h-4 w-4" /></Button>
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
            <AlertDialogDescription>Вы собираетесь удалить пользователя <span className="font-bold">{currentUser?.login}</span>. Это действие нельзя будет отменить.</AlertDialogDescription>
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