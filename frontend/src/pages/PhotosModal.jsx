import React, { useState, useEffect, useRef } from 'react';
import { getPhotoIds, uploadPhotos, deletePhoto } from '@/api/requestApi';
import SecureImage from '@/components/SecureImage';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog"; // <-- Импорт для подтверждения
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { X } from 'lucide-react';
import { useAuth } from '@/context/AuthProvider'; 

export default function PhotosModal({ isOpen, onClose, request }) {
    const [photoIds, setPhotoIds] = useState([]);
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [viewingPhotoId, setViewingPhotoId] = useState(null);
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
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
            loadPhotoIds();
        }
    }, [request, isOpen]);

    const handleFileChange = (e) => {
        const selectedFiles = Array.from(e.target.files);
        if (photoIds.length + selectedFiles.length > 10) {
            setError('Можно загрузить не более 10 фотографий в сумме.');
            e.target.value = null;
            setFiles([]);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }

            loadPhotoIds();

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

            loadPhotoIds();
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
            loadPhotoIds();
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
                                {canDelete && (
                                    <button
                                        onClick={() => setDeletingPhotoId(id)}
                                        className="absolute top-1 right-1 bg-red-600 text-white rounded-full p-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                                        aria-label="Удалить фото"
                                    >
                                        <X size={16} />
                                    </button>
                                )}

                            </div>
                        ))}
                    </div>
                    {canUpload && (
                        <div className="mt-4 pt-4 border-t">
                            <p className="text-sm text-muted-foreground">Загрузить новые фото (макс. 10)</p>
                            <div className="flex items-center gap-2 mt-2">
                                <Input 
                                    ref={fileInputRef}
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
                </DialogContent>
            </Dialog>

            <Dialog open={!!viewingPhotoId} onOpenChange={() => setViewingPhotoId(null)}>
                <DialogContent className="max-w-4xl max-h-[90vh] p-2">
                    <SecureImage photoId={viewingPhotoId} className="w-full h-full object-contain" />
                </DialogContent>
            </Dialog>

            <AlertDialog open={!!deletingPhotoId} onOpenChange={() => setDeletingPhotoId(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader><AlertDialogTitle>Вы уверены?</AlertDialogTitle><AlertDialogDescription>Вы собираетесь удалить это фото. Действие нельзя отменить.</AlertDialogDescription></AlertDialogHeader>
                    <AlertDialogFooter><AlertDialogCancel>Отмена</AlertDialogCancel><AlertDialogAction onClick={handleDelete}>Удалить</AlertDialogAction></AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}