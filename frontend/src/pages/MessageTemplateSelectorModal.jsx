import React, { useState, useMemo } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHeader, TableRow, TableHead } from "@/components/ui/table";
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function MessageTemplateSelectorModal({ isOpen, onClose, templates, onSelectTemplate }) {
    const [searchTerm, setSearchTerm] = useState('');

    const filteredTemplates = useMemo(() => {
        if (!searchTerm) {
            return templates;
        }
        const lowercasedFilter = searchTerm.toLowerCase();
        return templates.filter(template =>
            template.title.toLowerCase().includes(lowercasedFilter) ||
            template.message?.toLowerCase().includes(lowercasedFilter)
        );
    }, [templates, searchTerm]);

    const handleSelect = (template) => {
        onSelectTemplate(template);
        onClose();
    };

    return (
        <Dialog open={isOpen} onOpenChange={onClose}>
            <DialogContent className="max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Выбрать шаблон для вставки</DialogTitle>
                </DialogHeader>
                <div className="my-4">
                    <Input
                        placeholder="Поиск по названию или тексту..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>
                <div className="max-h-[60vh] overflow-y-auto border rounded-md">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Название</TableHead>
                                <TableHead>Начало сообщения</TableHead>
                                <TableHead className="w-[100px]">Действие</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {filteredTemplates.length > 0 ? (
                                filteredTemplates.map(template => (
                                    <TableRow key={template.messageID}>
                                        <TableCell className="font-medium">{template.title}</TableCell>
                                        <TableCell className="text-sm text-muted-foreground">
                                            {template.message ? (
                                                template.message.substring(0, 70) + (template.message.length > 70 ? '...' : '')
                                            ) : (
                                                <span className="italic">Нет текста</span>
                                            )}
                                        </TableCell>
                                        <TableCell>
                                            <Button variant="outline" size="sm" onClick={() => handleSelect(template)}>
                                                Вставить
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            ) : (
                                <TableRow>
                                    <TableCell colSpan={3} className="text-center">
                                        Шаблоны не найдены.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
            </DialogContent>
        </Dialog>
    );
}