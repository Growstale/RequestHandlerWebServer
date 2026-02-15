import React, { useState, useEffect } from 'react';
import { getComments, addComment, deleteComment } from '@/api/requestApi';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthProvider';
import { Trash2, Reply, X, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils'; 

export default function CommentsModal({ isOpen, onClose, request }) {
    const [comments, setComments] = useState([]);
    const [newComment, setNewComment] = useState('');
    const [replyTo, setReplyTo] = useState(null); 
    const [loading, setLoading] = useState(false);
    const { user } = useAuth();
    
    const [deletingComment, setDeletingComment] = useState(null); 
    const [apiError, setApiError] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const isAdmin = user?.role === 'RetailAdmin';
    const isClosed = request?.status === 'Closed';
    const canAddContent = !isClosed && user?.role !== 'StoreManager';

    const loadComments = () => {
        if (request?.requestID && isOpen) {
            setLoading(true);
            getComments(request.requestID)
                .then(res => setComments(res.data))
                .catch(() => setApiError("Не удалось загрузить комментарии."))
                .finally(() => setLoading(false));
        }
    };

    useEffect(loadComments, [request, isOpen]);

    const handleAddComment = async () => {
        if (newComment.trim() === '' || isSubmitting) return; 
        setApiError(null);
        setIsSubmitting(true);

        try {
            await addComment(request.requestID, { 
                commentText: newComment,
                parentCommentID: replyTo?.commentID 
            });
            setNewComment('');
            setReplyTo(null);
            loadComments();
        } catch (error) {
            setApiError(error.response?.data || "Ошибка при добавлении.");
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDelete = async () => {
        if (!deletingComment) return;
        try {
            await deleteComment(deletingComment.commentID); // Сервер удалит и детей сам
            setDeletingComment(null);
            loadComments();
        } catch (error) {
            setApiError("Ошибка при удалении.");
        }
    };

    return (
        <>
            <Dialog open={isOpen} onOpenChange={onClose}>
                <DialogContent className="max-w-2xl flex flex-col max-h-[90vh]">
                    <DialogHeader>
                        <DialogTitle>Комментарии к заявке #{request?.requestID}</DialogTitle>
                    </DialogHeader>
                    
                    {apiError && <p className="text-sm text-red-600 mb-2">{apiError}</p>}

                    <div className="flex-grow overflow-y-auto pr-4 space-y-4 custom-scrollbar">
                        {loading && <p className="text-center py-4">Загрузка...</p>}
                        {comments.map(c => (
                            <div key={c.commentID} className="space-y-3">
                                <div className="p-3 bg-gray-50 rounded-lg group border border-gray-100">
                                    <div className="flex justify-between items-start mb-1">
                                        <div className="text-xs">
                                            <span className="font-bold text-blue-700">{c.userLogin}</span>
                                            <span className="text-gray-400 ml-2">{new Date(c.createdAt).toLocaleString()}</span>
                                        </div>
                                        <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                            {canAddContent && (
                                                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setReplyTo(c)}>
                                                    <Reply className="h-3 w-3" />
                                                </Button>
                                            )}
                                            {isAdmin && (
                                                <Button variant="ghost" size="icon" className="h-6 w-6 text-red-400 hover:text-red-600" onClick={() => setDeletingComment(c)}>
                                                    <Trash2 className="h-3 w-3" />
                                                </Button>
                                            )}
                                        </div>
                                    </div>
                                    <p className="text-sm whitespace-pre-wrap">{c.commentText}</p>
                                </div>

                                {/* Ответы (Replies) */}
                                {c.replies?.map(r => (
                                    <div key={r.commentID} className="ml-8 p-2 bg-blue-50/50 border-l-2 border-blue-200 rounded-r-lg group">
                                        <div className="flex justify-between items-center mb-1">
                                            <div className="text-[10px]">
                                                <span className="font-bold text-blue-600">{r.userLogin}</span>
                                                <span className="text-gray-400 ml-2">{new Date(r.createdAt).toLocaleString()}</span>
                                            </div>
                                            {isAdmin && (
                                                <Button variant="ghost" size="icon" className="h-5 w-5 text-red-400 opacity-0 group-hover:opacity-100" onClick={() => setDeletingComment(r)}>
                                                    <Trash2 className="h-3 w-3" />
                                                </Button>
                                            )}
                                        </div>
                                        <p className="text-xs whitespace-pre-wrap">{r.commentText}</p>
                                    </div>
                                ))}
                            </div>
                        ))}
                    </div>

                    {canAddContent && (
                        <div className="mt-4 pt-4 border-t space-y-2">
                            {replyTo && (
                                <div className="flex justify-between items-center bg-blue-100 px-3 py-1.5 rounded-md text-xs">
                                    <span>Ответ пользователю <b>{replyTo.userLogin}</b></span>
                                    <button onClick={() => setReplyTo(null)} className="text-blue-600 hover:text-blue-800"><X size={14}/></button>
                                </div>
                            )}
                            <Textarea
                                placeholder={replyTo ? "Ваш ответ..." : "Написать комментарий..."}
                                value={newComment}
                                onChange={e => setNewComment(e.target.value)}
                                disabled={isSubmitting}
                                className="min-h-[80px]"
                            />
                            <div className="flex justify-end">
                                <Button onClick={handleAddComment} disabled={isSubmitting || !newComment.trim()}>
                                    {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
                                    {replyTo ? 'Ответить' : 'Отправить'}
                                </Button>
                            </div>
                        </div>
                    )}
                </DialogContent>
            </Dialog>

      <AlertDialog open={!!deletingComment} onOpenChange={() => setDeletingComment(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {deletingComment?.replies?.length > 0 
                ? "Удалить ветку комментариев?" 
                : "Удалить комментарий?"}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {deletingComment?.replies?.length > 0 ? (
                <span className="font-medium">
                  Внимание: у этого комментария есть ответы ({deletingComment.replies.length} шт.). 
                  При удалении родительского комментария вся ветка переписки будет безвозвратно удалена.
                </span>
              ) : (
                "Это действие необратимо. Вы уверены, что хотите удалить этот комментарий?"
              )}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Отмена</AlertDialogCancel>
            <AlertDialogAction 
              onClick={handleDelete} 
              className={cn(
                "bg-red-600 hover:bg-red-700",
                deletingComment?.replies?.length > 0 && "bg-destructive text-destructive-foreground animate-pulse"
              )}
            >
              {deletingComment?.replies?.length > 0 ? "Да, удалить всё" : "Удалить"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
        </>
    );
}