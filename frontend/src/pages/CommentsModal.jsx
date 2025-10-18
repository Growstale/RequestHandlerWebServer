import React, { useState, useEffect } from 'react';
import { getComments, addComment } from '@/api/requestApi';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/context/AuthProvider';

export default function CommentsModal({ isOpen, onClose, request }) {
    const [comments, setComments] = useState([]);
    const [newComment, setNewComment] = useState('');
    const [loading, setLoading] = useState(false);
    const { user } = useAuth();
    const isClosed = request?.status === 'Closed';

    useEffect(() => {
        if (request?.requestID && isOpen) {
            setLoading(true);
            getComments(request.requestID)
                .then(res => setComments(res.data))
                .catch(console.error)
                .finally(() => setLoading(false));
        }
    }, [request, isOpen]);

    const handleAddComment = async () => {
        if (newComment.trim() === '') return;
        try {
            const response = await addComment(request.requestID, { commentText: newComment });
            setComments(prev => [...prev, response.data]);
            setNewComment('');
        } catch (error) {
            console.error("Failed to add comment", error);
        }
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Комментарии к заявке #{request?.requestID}</DialogTitle>
                    <DialogDescription>{request?.title}</DialogDescription>
                </DialogHeader>
                <div className="mt-4 max-h-[60vh] overflow-y-auto pr-4 space-y-4">
                    {loading && <p>Загрузка...</p>}
                    {comments.map(c => (
                        <div key={c.commentID} className="p-3 bg-gray-100 rounded-lg">
                            <div className="flex justify-between items-center text-sm mb-1">
                                <span className="font-bold">{c.userLogin}</span>
                                <span className="text-gray-500">{new Date(c.createdAt).toLocaleString()}</span>
                            </div>
                            <p>{c.commentText}</p>
                        </div>
                    ))}
                </div>
                {!isClosed && (
                    <div className="mt-4 pt-4 border-t">
                        <Textarea
                            placeholder="Написать комментарий..."
                            value={newComment}
                            onChange={e => setNewComment(e.target.value)}
                            disabled={isClosed} // <-- Можно еще и так
                        />
                        <Button onClick={handleAddComment} className="mt-2" disabled={isClosed}>Отправить</Button>
                    </div>
                )}
            </DialogContent>
        </Dialog>
    );
}