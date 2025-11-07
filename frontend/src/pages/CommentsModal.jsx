// src/pages/CommentsModal.jsx

import React, { useState, useEffect } from 'react';
import { getComments, addComment, deleteComment } from '@/api/requestApi';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthProvider';
import { Trash2 } from 'lucide-react';

export default function CommentsModal({ isOpen, onClose, request }) {
    const [comments, setComments] = useState([]);
    const [newComment, setNewComment] = useState('');
    const [loading, setLoading] = useState(false);
    const { user } = useAuth();
    
    const [deletingComment, setDeletingComment] = useState(null); 
    const [apiError, setApiError] = useState(null);

    const isAdmin = user?.role === 'RetailAdmin';
    const isClosed = request?.status === 'Closed';
    const canAddContent = !isClosed && user?.role !== 'StoreManager';

    useEffect(() => {
        if (request?.requestID && isOpen) {
            setApiError(null);
            setLoading(true);
            getComments(request.requestID)
                .then(res => setComments(res.data))
                .catch(err => {
                    console.error("Failed to load comments", err);
                    setApiError("Не удалось загрузить комментарии.");
                })
                .finally(() => setLoading(false));
        }
    }, [request, isOpen]);

    const handleAddComment = async () => {
        if (newComment.trim() === '') return;
        setApiError(null);
        try {
            const response = await addComment(request.requestID, { commentText: newComment });
            setComments(prev => [...prev, response.data]);
            setNewComment('');
        } catch (error) {
            console.error("Failed to add comment", error);
            setApiError(error.response?.data || "Не удалось добавить комментарий.");
        }
    };

    const handleDelete = async () => {
        if (!deletingComment) return;
        setApiError(null);
        try {
            await deleteComment(deletingComment.commentID);
            setComments(prev => prev.filter(c => c.commentID !== deletingComment.commentID));
            setDeletingComment(null);
        } catch (error) {
            console.error("Failed to delete comment", error);
            setApiError(error.response?.data || "Не удалось удалить комментарий.");
            setDeletingComment(null);
        }
    };

    return (
        <>
            <Dialog open={isOpen} onOpenChange={onClose}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>Комментарии к заявке #{request?.requestID}</DialogTitle>
                        {/* Исправлено: request?.title на request?.description */}
                        <DialogDescription>{request?.description?.substring(0, 100)}...</DialogDescription> 
                    </DialogHeader>
                    {apiError && <p className="text-sm text-red-600 mt-2">{apiError}</p>}
                    <div className="mt-4 max-h-[60vh] overflow-y-auto pr-4 space-y-4">
                        {loading && <p>Загрузка...</p>}
                        {comments.map(c => (
                            // --- V-- НАЧАЛО ИСПРАВЛЕНИЯ --V ---
                            <div key={c.commentID} className="p-3 bg-gray-50 rounded-lg group">
                                <div className="flex justify-between items-start text-sm mb-1">
                                    {/* Блок с информацией о пользователе */}
                                    <div>
                                        <span className="font-bold">{c.userLogin}</span>
                                        <span className="text-gray-500 ml-2">{new Date(c.createdAt).toLocaleString()}</span>
                                    </div>

                                    {/* Кнопка удаления, видна только админу. Теперь она ВНУТРИ flex-контейнера */}
                                    {isAdmin && (
                                        <Button 
                                            variant="ghost" 
                                            size="icon" 
                                            className="h-6 w-6 text-gray-400 hover:text-red-600 opacity-0 group-hover:opacity-100 transition-opacity"
                                            onClick={() => setDeletingComment(c)}
                                        >
                                            <Trash2 className="h-4 w-4" />
                                        </Button>
                                    )}
                                </div>
                                <p className="whitespace-pre-wrap">{c.commentText}</p>
                            </div>
                            // --- ^-- КОНЕЦ ИСПРАВЛЕНИЯ --^ ---
                        ))}
                    </div>
                    {canAddContent && (
                        <div className="mt-4 pt-4 border-t">
                            <Textarea
                                placeholder="Написать комментарий..."
                                value={newComment}
                                onChange={e => setNewComment(e.target.value)}
                            />
                            <Button onClick={handleAddComment} className="mt-2">Отправить</Button>
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            <AlertDialog open={!!deletingComment} onOpenChange={() => setDeletingComment(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Вы уверены?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Вы собираетесь удалить комментарий пользователя <strong>{deletingComment?.userLogin}</strong>. Это действие нельзя отменить.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Отмена</AlertDialogCancel>
                        <AlertDialogAction onClick={handleDelete} className="bg-destructive hover:bg-destructive/90">Удалить</AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}