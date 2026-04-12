import React, { useState, useEffect, useRef, useCallback } from 'react';
import { getPhotoIds, uploadPhotos, deletePhoto } from '@/api/requestApi';
import SecureImage from '@/components/SecureImage';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { X, ChevronLeft, ChevronRight, Trash2, Loader2, Camera } from 'lucide-react';
import { useAuth } from '@/context/AuthProvider'; 
import { Label } from '@/components/ui/label'; 
import { cn } from '@/lib/utils'; 

export default function PhotosModal({ isOpen, onClose, request, hasNew }) {
    const [photoIds, setPhotoIds] = useState([]);
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(false);
    const [isProcessing, setIsProcessing] = useState(false); // Для процесса сжатия
    const [error, setError] = useState('');
    const [viewerIndex, setViewerIndex] = useState(null); 
    const [deletingPhotoId, setDeletingPhotoId] = useState(null);
    
    const isClosed = request?.status === 'Closed';
    const fileInputRef = useRef(null);
    const { user } = useAuth();

    const canUpload = !isClosed && (user?.role === 'RetailAdmin' || user?.role === 'Contractor' || user?.role === 'Moderator');
    const canDelete = !isClosed && (user?.role === 'RetailAdmin' || user?.role === 'Moderator');

    // --- Функция сжатия изображения ---
    const compressImage = (file) => {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = (event) => {
                const img = new Image();
                img.src = event.target.result;
                img.onload = () => {
                    const canvas = document.createElement('canvas');
                    const MAX_WIDTH = 1280; // Максимальная ширина
                    const MAX_HEIGHT = 1280; // Максимальная высота
                    let width = img.width;
                    let height = img.height;

                    if (width > height) {
                        if (width > MAX_WIDTH) {
                            height *= MAX_WIDTH / width;
                            width = MAX_WIDTH;
                        }
                    } else {
                        if (height > MAX_HEIGHT) {
                            width *= MAX_HEIGHT / height;
                            height = MAX_HEIGHT;
                        }
                    }

                    canvas.width = width;
                    canvas.height = height;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0, width, height);

                    canvas.toBlob((blob) => {
                        if (blob) {
                            // Создаем новый файл из блоба (всегда JPEG для экономии места)
                            const compressedFile = new File([blob], file.name.replace(/\.[^/.]+$/, "") + ".jpg", {
                                type: 'image/jpeg',
                                lastModified: Date.now()
                            });
                            resolve(compressedFile);
                        } else {
                            reject(new Error("Ошибка при сжатии"));
                        }
                    }, 'image/jpeg', 0.7); // Качество 0.7
                };
                img.onerror = reject;
            };
            reader.onerror = reject;
        });
    };

    const loadPhotoIds = () => {
        if (request?.requestID) {
            setLoading(true);
            getPhotoIds(request.requestID)
                .then(res => setPhotoIds(res.data))
                .catch(console.error)
                .finally(() => setLoading(false));
        }
    };

    const handlePaste = useCallback(async (e) => {
        if (!isOpen || !canUpload || isProcessing || loading) return;

        const items = e.clipboardData.items;
        const pastedFiles = [];
        for (let i = 0; i < items.length; i++) {
            if (items[i].type.indexOf("image") !== -1) {
                const file = items[i].getAsFile();
                if (file) pastedFiles.push(file);
            }
        }

        if (pastedFiles.length > 0) {
            if (photoIds.length + files.length + pastedFiles.length > 10) {
                setError('Можно загрузить не более 10 фотографий.');
                return;
            }
            
            setIsProcessing(true);
            setError('');
            try {
                const compressed = await Promise.all(pastedFiles.map(f => compressImage(f)));
                setFiles(prev => [...prev, ...compressed]);
            } catch (err) {
                setError("Ошибка при обработке изображения.");
            } finally {
                setIsProcessing(false);
            }
        }
    }, [isOpen, canUpload, photoIds.length, files.length, isProcessing, loading]);

    useEffect(() => {
        if (isOpen) {
            window.addEventListener('paste', handlePaste);
        }
        return () => window.removeEventListener('paste', handlePaste);
    }, [isOpen, handlePaste]);

    useEffect(() => {
        if (isOpen) {
            setPhotoIds([]);
            setError('');
            setFiles([]);
            setViewerIndex(null);
            loadPhotoIds();
        }
    }, [request, isOpen]);

    const handleFileChange = async (e) => {
        const selectedFiles = Array.from(e.target.files);
        if (selectedFiles.length === 0) return;

        if (photoIds.length + files.length + selectedFiles.length > 10) {
            setError('Можно загрузить не более 10 фотографий.');
            e.target.value = null;
            return;
        }

        setIsProcessing(true);
        setError('');
        try {
            const compressed = await Promise.all(selectedFiles.map(f => compressImage(f)));
            setFiles(prev => [...prev, ...compressed]);
        } catch (err) {
            setError("Ошибка при обработке файлов.");
        } finally {
            setIsProcessing(false);
            e.target.value = null; 
        }
    };

    const handleUpload = async () => {
        if (files.length === 0 || loading) return;
        setLoading(true);
        try {
            await uploadPhotos(request.requestID, files);
            setFiles([]);
            setError('');
            loadPhotoIds();
        } catch (err) {
            setError(err.response?.data || "Ошибка загрузки");
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async () => {
        if (!deletingPhotoId) return;
        try {
            await deletePhoto(deletingPhotoId);
            setDeletingPhotoId(null);
            if (viewerIndex !== null && photoIds[viewerIndex] === deletingPhotoId) {
                setViewerIndex(null);
            }
            loadPhotoIds();
        } catch (err) {
            setError(err.response?.data || "Ошибка удаления");
        }
    };

    const handlePrev = useCallback(() => {
        setViewerIndex(prev => (prev === null || prev === 0 ? photoIds.length - 1 : prev - 1));
    }, [photoIds.length]);

    const handleNext = useCallback(() => {
        setViewerIndex(prev => (prev === null || prev === photoIds.length - 1 ? 0 : prev + 1));
    }, [photoIds.length]);

    useEffect(() => {
        const handleKeyDown = (e) => {
            if (viewerIndex === null) return;
            if (e.key === 'ArrowLeft') handlePrev();
            if (e.key === 'ArrowRight') handleNext();
            if (e.key === 'Escape') setViewerIndex(null);
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [viewerIndex, handlePrev, handleNext]);

    return (
        <>
            <Dialog open={isOpen} onOpenChange={onClose}>
                <DialogContent className="max-w-4xl w-[95vw] sm:w-full max-h-[95vh] md:max-h-[90vh] overflow-y-auto p-4 sm:p-6 rounded-xl">
                    <DialogHeader>
                        <DialogTitle>Фото к заявке #{request?.requestID}</DialogTitle>
                        <DialogDescription className="hidden">Просмотр и загрузка фотографий к заявке</DialogDescription>
                    </DialogHeader>
                    
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 max-h-[50vh] overflow-y-auto p-1 custom-scrollbar">
                        {loading && photoIds.length === 0 && <p className="col-span-full text-center py-4">Загрузка...</p>}
                        {!loading && photoIds.length === 0 && files.length === 0 && (
                            <p className="col-span-full text-center text-gray-500 py-4 italic">Нет загруженных фотографий.</p>
                        )}
                        {photoIds.map((id, index) => (
                            <div key={id} className="relative group aspect-square">
                                <button onClick={() => setViewerIndex(index)} className={cn("w-full h-full block rounded-lg overflow-hidden border shadow-sm hover:shadow-md transition-all duration-500", hasNew && index === photoIds.length - 1 ? "border-4 border-yellow-400 scale-105" : "border-gray-200")}>
                                    <SecureImage photoId={id} className="w-full h-full object-cover transition-transform group-hover:scale-110" />
                                </button>
                                {canDelete && (
                                    <button onClick={(e) => { e.stopPropagation(); setDeletingPhotoId(id); }} className="absolute top-1 right-1 bg-white/90 text-red-600 hover:bg-red-100 rounded-full p-1.5 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm">
                                        <Trash2 size={16} />
                                    </button>
                                )}
                            </div>
                        ))}
                    </div>

                    {files.length > 0 && (
                        <div className="mt-4 p-4 bg-blue-50/50 border border-blue-100 rounded-xl animate-in fade-in slide-in-from-bottom-2">
                            <div className="flex justify-between items-center mb-3">
                                <h4 className="text-sm font-semibold text-blue-800 flex items-center gap-2">
                                    <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
                                    Выбрано для загрузки ({files.length})
                                </h4>
                                <Button variant="ghost" size="sm" onClick={() => setFiles([])} disabled={loading} className="h-7 text-xs text-blue-600 hover:text-blue-800 hover:bg-blue-100">
                                    Отменить всё
                                </Button>
                            </div>
                            <div className="flex flex-wrap gap-3 mb-4">
                                {files.map((file, idx) => (
                                    <div key={idx} className="relative w-20 h-20 group">
                                        <img 
                                            src={URL.createObjectURL(file)} 
                                            className="w-full h-full object-cover rounded-lg border-2 border-white shadow-sm"
                                            onLoad={(e) => URL.revokeObjectURL(e.target.src)} 
                                        />
                                        <button onClick={() => setFiles(prev => prev.filter((_, i) => i !== idx))} className="absolute -top-1.5 -right-1.5 bg-red-500 text-white rounded-full p-0.5 shadow-md hover:bg-red-600 transition-colors">
                                            <X size={12} />
                                        </button>
                                    </div>
                                ))}
                            </div>
                            <Button onClick={handleUpload} disabled={loading || isProcessing} className="w-full bg-blue-600 hover:bg-blue-700 shadow-lg py-6 text-base font-bold">
                                {loading ? <><Loader2 className="mr-2 animate-spin" /> Загрузка...</> : `Подтвердить и загрузить (${files.length})`}
                            </Button>
                        </div>
                    )}

                    {canUpload && photoIds.length + files.length < 10 && (
                        <div className="mt-4 pt-4 border-t">
                            <Label htmlFor="file-upload" className="w-full">
                                <div className={cn(
                                    "border-2 border-dashed rounded-lg p-4 transition-colors text-center",
                                    isProcessing ? "bg-yellow-50 border-yellow-300" : "border-gray-200 hover:bg-gray-50 cursor-pointer"
                                )}>
                                    {isProcessing ? (
                                        <div className="flex flex-col items-center gap-2">
                                            <Loader2 className="h-6 w-6 animate-spin text-yellow-600" />
                                            <p className="text-sm text-yellow-700 font-medium">Сжатие и подготовка фото...</p>
                                        </div>
                                    ) : (
                                        <>
                                            <p className="text-sm text-gray-600 font-medium">Нажмите для выбора фото или вставьте из буфера (Ctrl+V)</p>
                                            <p className="text-xs text-gray-400 mt-1">Фотографии автоматически сжимаются для экономии места</p>
                                        </>
                                    )}
                                </div>
                                <input 
                                    id="file-upload"
                                    type="file" 
                                    multiple 
                                    onChange={handleFileChange} 
                                    accept="image/*" 
                                    className="hidden" 
                                    disabled={isProcessing || loading}
                                />
                            </Label>
                            {error && <p className="text-sm text-red-600 mt-2 text-center font-medium">{error}</p>}
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            {/* Просмотрщик (Lightbox) - оставлен без изменений */}
            <Dialog open={viewerIndex !== null} onOpenChange={(open) => !open && setViewerIndex(null)}>
                <DialogContent className="max-w-[95vw] h-[90vh] p-0 border-none bg-transparent shadow-none flex flex-col justify-center items-center outline-none">
                    <button onClick={() => setViewerIndex(null)} className="absolute top-4 right-4 z-50 p-2 bg-black/50 text-white rounded-full hover:bg-black/70 transition-colors">
                        <X size={24} />
                    </button>
                    <div className="relative w-full h-full flex items-center justify-center">
                        {photoIds.length > 1 && (
                            <button onClick={handlePrev} className="absolute left-2 md:left-8 z-50 p-3 bg-black/40 text-white rounded-full hover:bg-black/60 transition-all focus:outline-none">
                                <ChevronLeft size={32} />
                            </button>
                        )}
                        {viewerIndex !== null && photoIds[viewerIndex] && (
                            <div className="w-full h-full flex items-center justify-center p-2 md:p-12">
                                <SecureImage key={photoIds[viewerIndex]} photoId={photoIds[viewerIndex]} className="max-w-full max-h-full object-contain rounded-md shadow-2xl" />
                            </div>
                        )}
                        {photoIds.length > 1 && (
                            <button onClick={handleNext} className="absolute right-2 md:right-8 z-50 p-3 bg-black/40 text-white rounded-full hover:bg-black/60 transition-all focus:outline-none">
                                <ChevronRight size={32} />
                            </button>
                        )}
                    </div>
                    {photoIds.length > 0 && (
                        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 bg-black/50 text-white px-4 py-1 rounded-full text-sm">
                            {viewerIndex + 1} / {photoIds.length}
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            <AlertDialog open={!!deletingPhotoId} onOpenChange={() => setDeletingPhotoId(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        <AlertDialogDescription>Вы собираетесь удалить это фото. Действие нельзя отменить.</AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDelete} className="bg-red-600 hover:bg-red-700">Удалить</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}