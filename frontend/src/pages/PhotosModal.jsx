import React, { useState, useEffect, useRef, useCallback } from 'react';
import { getPhotoIds, uploadPhotos, deletePhoto } from '@/api/requestApi';
import SecureImage from '@/components/SecureImage';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { X, ChevronLeft, ChevronRight, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthProvider'; 

export default function PhotosModal({ isOpen, onClose, request }) {
    const [photoIds, setPhotoIds] = useState([]);
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [viewerIndex, setViewerIndex] = useState(null); 
    
    const [deletingPhotoId, setDeletingPhotoId] = useState(null);
    
    const isClosed = request?.status === 'Closed';
    const fileInputRef = useRef(null);
    const { user } = useAuth();

    const canUpload = !isClosed && (user?.role === 'RetailAdmin' || user?.role === 'Contractor');
    const canDelete = !isClosed && user?.role === 'RetailAdmin';

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
            setPhotoIds([]);
            setError('');
            setFiles([]);
            setViewerIndex(null);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
            loadPhotoIds();
        }
    }, [request, isOpen]);

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

    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
        const MAX_SIZE = 5 * 1024 * 1024;

        if (photoIds.length + selectedFiles.length > 10) {
            setError('Можно загрузить не более 10 фотографий в сумме.');
            e.target.value = null;
            return;
        }

        const oversizedFile = selectedFiles.find(f => f.size > MAX_SIZE);
        if (oversizedFile) {
            setError(`Файл ${oversizedFile.name} слишком большой (макс. 5МБ).`);
            e.target.value = null;
            return;
        }

        setError('');
        setFiles(selectedFiles);
    };

    const handleUpload = async () => {
        if (files.length === 0) return;
        try {
            await uploadPhotos(request.requestID, files);
            setFiles([]);
            if (fileInputRef.current) fileInputRef.current.value = '';
            loadPhotoIds();
        } catch (err) {
            setError(err.response?.data || "Ошибка загрузки");
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

    return (
        <>
            <Dialog open={isOpen} onOpenChange={onClose}>
                <DialogContent className="max-w-4xl w-full">
                    <DialogHeader>
                        <DialogTitle>Фото к заявке #{request?.requestID}</DialogTitle>
                    </DialogHeader>
                    
                    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 max-h-[60vh] overflow-y-auto p-1">
                        {loading && <p className="col-span-full text-center py-4">Загрузка...</p>}
                        
                        {!loading && photoIds.length === 0 && (
                            <p className="col-span-full text-center text-gray-500 py-4">Нет фотографий.</p>
                        )}

                        {photoIds.map((id, index) => (
                            <div key={id} className="relative group aspect-square">
                                <button 
                                    onClick={() => setViewerIndex(index)} 
                                    className="w-full h-full block rounded-lg overflow-hidden border border-gray-200 shadow-sm hover:shadow-md transition-all"
                                >
                                    <SecureImage
                                        photoId={id}
                                        // object-cover: заполняет квадрат, обрезая лишнее (красивая сетка)
                                        className="w-full h-full object-cover transition-transform group-hover:scale-105"
                                    />
                                </button>
                                
                                {canDelete && (
                                    <button
                                        onClick={(e) => { e.stopPropagation(); setDeletingPhotoId(id); }}
                                        className="absolute top-1 right-1 bg-white/90 text-red-600 hover:bg-red-100 rounded-full p-1.5 opacity-0 group-hover:opacity-100 transition-opacity shadow-sm"
                                        title="Удалить фото"
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                )}
                            </div>
                        ))}
                    </div>

                    {canUpload && (
                        <div className="mt-4 pt-4 border-t">
                            <p className="text-sm text-muted-foreground mb-2">Загрузить новые фото (макс. 10)</p>
                            <div className="flex flex-col sm:flex-row items-center gap-2">
                                <Input 
                                    ref={fileInputRef}
                                    type="file" 
                                    multiple 
                                    onChange={handleFileChange} 
                                    accept="image/*" 
                                    className="flex-grow" 
                                />
                                <Button onClick={handleUpload} disabled={files.length === 0} className="w-full sm:w-auto">
                                    Загрузить
                                </Button>
                            </div>
                            {error && <p className="text-sm text-red-600 mt-2">{error}</p>}
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            <Dialog open={viewerIndex !== null} onOpenChange={(open) => !open && setViewerIndex(null)}>
                <DialogContent className="max-w-[95vw] h-[90vh] p-0 border-none bg-transparent shadow-none flex flex-col justify-center items-center outline-none">
                    
                    <button 
                        onClick={() => setViewerIndex(null)}
                        className="absolute top-4 right-4 z-50 p-2 bg-black/50 text-white rounded-full hover:bg-black/70 transition-colors"
                    >
                        <X size={24} />
                    </button>

                    <div className="relative w-full h-full flex items-center justify-center">
                        
                        {photoIds.length > 1 && (
                            <button 
                                onClick={handlePrev}
                                className="absolute left-2 md:left-8 z-50 p-3 bg-black/40 text-white rounded-full hover:bg-black/60 transition-all focus:outline-none"
                            >
                                <ChevronLeft size={32} />
                            </button>
                        )}

                        {viewerIndex !== null && photoIds[viewerIndex] && (
                            <div className="w-full h-full flex items-center justify-center p-2 md:p-12">
                                <SecureImage 
                                    key={photoIds[viewerIndex]}
                                    photoId={photoIds[viewerIndex]} 
                                    className="max-w-full max-h-full object-contain rounded-md shadow-2xl"
                                />
                            </div>
                        )}

                        {photoIds.length > 1 && (
                            <button 
                                onClick={handleNext}
                                className="absolute right-2 md:right-8 z-50 p-3 bg-black/40 text-white rounded-full hover:bg-black/60 transition-all focus:outline-none"
                            >
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
                        <AlertDialogDescription>
                            Вы собираетесь удалить это фото. Действие нельзя отменить.
                        </AlertDialogDescription>
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