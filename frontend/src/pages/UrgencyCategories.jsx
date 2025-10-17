import React, { useState, useEffect, useCallback, useMemo } from 'react' 
import { getUrgencyCategories, updateUrgencyCategory } from '@/api/urgencyCategoryApi'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle
} from "@/components/ui/dialog"
import { Edit } from 'lucide-react'
import UrgencyCategoryForm from './UrgencyCategoryForm'
import { getUrgencyDisplayName } from '@/lib/displayNames' 

export default function UrgencyCategories() {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [isFormOpen, setIsFormOpen] = useState(false)
  const [currentCategory, setCurrentCategory] = useState(null)
  const [formApiError, setFormApiError] = useState(null)

  const reloadCategories = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getUrgencyCategories();
      setCategories(response.data);
    } catch (err) {
      setError(err.response?.data || 'Не удалось загрузить список категорий');
    } finally {
      setLoading(false);
    }
  }, []);
  
  useEffect(() => {
    reloadCategories();
  }, [reloadCategories]);

const sortedCategories = useMemo(() => {
    return [...categories].sort((a, b) => a.defaultDays - b.defaultDays);
  }, [categories]);

  const handleFormSubmit = async (formData) => {
    if (!currentCategory) return;
    setFormApiError(null);
    try {
      await updateUrgencyCategory(currentCategory.urgencyID, formData);
      setIsFormOpen(false);
      reloadCategories();
    } catch (err) {
      setFormApiError(err.response?.data || 'Произошла ошибка');
    }
  };

  const openEditForm = (category) => {
    setCurrentCategory(category);
    setFormApiError(null);
    setIsFormOpen(true);
  };

  return (
    <main className="container mx-auto p-6">
      <div className="flex justify-between items-center mb-4">
        <h1 className="text-3xl font-semibold">Настройка срочности заявок</h1>
      </div>
      
      <p className="text-sm text-muted-foreground mb-4">
        Здесь вы можете настроить количество дней по умолчанию для каждого типа срочности. Создание и удаление этих категорий недоступно.
      </p>

      {loading && <p>Загрузка...</p>}
      {error && <p className="text-red-500">{error}</p>}
      
      {!loading && !error && (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Название</TableHead>
                <TableHead>Дней на выполнение</TableHead>
                <TableHead className="w-[100px]">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedCategories.map(category => (
                <TableRow key={category.urgencyID}>
                  <TableCell className="font-medium">{getUrgencyDisplayName(category.urgencyName)}</TableCell>
                  <TableCell>{category.defaultDays}</TableCell>
                  <TableCell>
                    <Button variant="outline" size="icon" onClick={() => openEditForm(category)}>
                      <Edit className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Редактировать категорию срочности</DialogTitle>
            <DialogDescription>
              Вы редактируете количество дней для категории "{getUrgencyDisplayName(currentCategory?.urgencyName)}".
            </DialogDescription>
          </DialogHeader>
          {currentCategory && (
            <UrgencyCategoryForm 
              key={currentCategory.urgencyID}
              currentCategory={currentCategory}
              onSubmit={handleFormSubmit} 
              onCancel={() => setIsFormOpen(false)}
              apiError={formApiError}
            />
          )}
        </DialogContent>
      </Dialog>
    </main>
  );
}