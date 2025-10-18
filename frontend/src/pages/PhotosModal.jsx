import React, { useState, useEffect, useRef } from 'react'; // <-- Добавьте useRef
import { getPhotoIds, uploadPhotos, deletePhoto } from '@/api/requestApi';
import SecureImage from '@/components/SecureImage';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog"; // <-- Импорт для подтверждения
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { X } from 'lucide-react';

export default function PhotosModal({ isOpen, onClose, request }) {
    const [photoIds, setPhotoIds] = useState([]); // <-- Храним ID, а не URL
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [viewingPhotoId, setViewingPhotoId] = useState(null);
    const [deletingPhotoId, setDeletingPhotoId] = useState(null);
    const isClosed = request?.status === 'Closed';
    const fileInputRef = useRef(null); // <-- 1. Создайте ref

    const loadPhotoIds = () => {
        if (request?.requestID) {
            setLoading(true);
            getPhotoIds(request.requestID)
                .then(res => setPhotoIds(res.data))
                .catch(console.error)
                .finally(() => setLoading(false));
        }
    };

    useEffect(() => {
        if (isOpen) {
            // vvv ДОБАВЬТЕ ЯВНУЮ ОЧИСТКУ СОСТОЯНИЯ ПЕРЕД ЗАГРУЗКОЙ vvv
            setPhotoIds([]); // Очищаем старые ID
            setError('');    // Сбрасываем ошибки
            setFiles([]);    // Сбрасываем выбранные файлы
            if (fileInputRef.current) {
                fileInputRef.current.value = ''; // И очищаем input
            }
            // ^^^
            loadPhotoIds();
        }
    }, [request, isOpen]); // Зависимость от request гарантирует, что это сработает при смене заявки

    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
        if (photoIds.length + selectedFiles.length > 10) {
            setError('Можно загрузить не более 10 фотографий в сумме.');
            e.target.value = null; // Сброс инпута
            setFiles([]);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }

            loadPhotoIds(); // Перезагружаем ID фото

        } else {
            setError('');
            setFiles(selectedFiles);
        }
    };

    const handleUpload = async () => {
        if (files.length === 0) return;
        try {
            await uploadPhotos(request.requestID, files);
            setFiles([]);

            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }

            loadPhotoIds(); // Перезагружаем ID фото
        } catch (err) {
            setError(err.response?.data || "Ошибка загрузки");
            console.error("Failed to upload photos", err);
        }
    };

    const handleDelete = async () => {
        if (!deletingPhotoId) return;
        try {
            await deletePhoto(deletingPhotoId);
            setDeletingPhotoId(null);
            loadPhotoIds(); // Перезагружаем список фото
        } catch (err) {
            setError(err.response?.data || "Ошибка удаления");
            console.error("Failed to delete photo", err);
        }
    };

return (
        <>
            <Dialog open={isOpen} onOpenChange={onClose}>
                <DialogContent className="max-w-4xl">
                    <DialogHeader>
                        <DialogTitle>Фото к заявке #{request?.requestID}</DialogTitle>
                    </DialogHeader>
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-4 max-h-[60vh] overflow-y-auto p-1">
                        {loading && <p>Загрузка...</p>}
                        {photoIds.map(id => (
                            <div key={id} className="relative group">
                                <button onClick={() => setViewingPhotoId(id)} className="w-full h-full">
                                    <SecureImage
                                        photoId={id}
                                        className="rounded-lg w-full h-32 object-cover transition-transform group-hover:scale-105"
                                    />
                                </button>
                                {/* vvv Кнопка удаления vvv */}
                                {!isClosed && (
                                    <button
                                        onClick={() => setDeletingPhotoId(id)}
                                        className="absolute top-1 right-1 bg-red-600 text-white rounded-full p-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                                        aria-label="Удалить фото"
                                    >
                                        <X size={16} />
                                    </button>
                                )}
                                {/* ^^^ */}
                            </div>
                        ))}
                    </div>
                    {/* vvv Блок загрузки (теперь с проверкой статуса) vvv */}
                    {!isClosed && (
                        <div className="mt-4 pt-4 border-t">
                            <p className="text-sm text-muted-foreground">Загрузить новые фото (макс. 10)</p>
                            <div className="flex items-center gap-2 mt-2">
                                <Input 
                                    ref={fileInputRef} // <-- 3. Привяжите ref к input
                                    type="file" 
                                    multiple 
                                    onChange={handleFileChange} 
                                    accept="image/*" 
                                    className="flex-grow" 
                                />
                                <Button onClick={handleUpload} disabled={files.length === 0}>Загрузить</Button>
                            </div>
                            {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
                        </div>
                    )}
                    {/* ^^^ */}
                </DialogContent>
            </Dialog>

            {/* vvv Модальное окно для просмотра фото vvv */}
            <Dialog open={!!viewingPhotoId} onOpenChange={() => setViewingPhotoId(null)}>
                <DialogContent className="max-w-4xl max-h-[90vh] p-2">
                    <SecureImage photoId={viewingPhotoId} className="w-full h-full object-contain" />
                </DialogContent>
            </Dialog>
            {/* ^^^ */}

            {/* vvv Диалог подтверждения удаления vvv */}
            <AlertDialog open={!!deletingPhotoId} onOpenChange={() => setDeletingPhotoId(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader><AlertDialogTitle>Вы уверены?</AlertDialogTitle><AlertDialogDescription>Вы собираетесь удалить это фото. Действие нельзя отменить.</AlertDialogDescription></AlertDialogHeader>
                    <AlertDialogFooter><AlertDialogCancel>Отмена</AlertDialogCancel><AlertDialogAction onClick={handleDelete}>Удалить</AlertDialogAction></AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
            {/* ^^^ */}
        </>
    );
}