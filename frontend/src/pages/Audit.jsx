import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { auditApi } from '@/api/auditApi';
import { logger } from '@/lib/logger';
import { Filter, Plus, Edit, Trash2, Eye, X } from 'lucide-react';
import Pagination from '@/components/Pagination';

export default function Audit() {
    const [auditLogs, setAuditLogs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [size] = useState(50);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    
    // Filters
    const [tableName, setTableName] = useState('ALL');
    const [action, setAction] = useState('ALL');
    const [userID, setUserID] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    const [stats, setStats] = useState(null);
    const [selectedLog, setSelectedLog] = useState(null);
    const [isDetailsOpen, setIsDetailsOpen] = useState(false);

    const tableNames = [
        'Shops', 'Users', 'Requests', 'WorkCategories', 
        'UrgencyCategories', 'Notifications', 'MessageTemplates', 
        'ShopContractorChats'
    ];

    useEffect(() => {
        fetchAuditLogs();
        fetchStats();
    }, [page, tableName, action, userID, startDate, endDate]);

    const fetchAuditLogs = async () => {
        try {
            setLoading(true);
            const params = {
                page,
                size,
                ...(tableName && tableName !== 'ALL' && { tableName }),
                ...(action && action !== 'ALL' && { action }),
                ...(userID && { userID: parseInt(userID) }),
                ...(startDate && { startDate }),
                ...(endDate && { endDate })
            };
            const response = await auditApi.getAuditLogs(params);
            setAuditLogs(response.data.content);
            setTotalPages(response.data.totalPages);
            setTotalElements(response.data.totalElements);
        } catch (err) {
            logger.error('Audit logs', err);
            setError('Не удалось загрузить записи аудита');
        } finally {
            setLoading(false);
        }
    };

    const fetchStats = async () => {
        try {
            const params = {};
            if (startDate) params.startDate = startDate;
            if (endDate) params.endDate = endDate;
            const response = await auditApi.getAuditStats(params);
            setStats(response.data);
        } catch (err) {
            logger.error('Audit stats', err);
        }
    };

    const getActionIcon = (action) => {
        switch (action) {
            case 'CREATE':
                return <Plus className="h-4 w-4 text-green-500" />;
            case 'UPDATE':
                return <Edit className="h-4 w-4 text-blue-500" />;
            case 'DELETE':
                return <Trash2 className="h-4 w-4 text-red-500" />;
            default:
                return null;
        }
    };

    const getActionColor = (action) => {
        switch (action) {
            case 'CREATE':
                return 'text-green-600 bg-green-50';
            case 'UPDATE':
                return 'text-blue-600 bg-blue-50';
            case 'DELETE':
                return 'text-red-600 bg-red-50';
            default:
                return 'text-gray-600 bg-gray-50';
        }
    };

    const formatDate = (dateString) => {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleString('ru-RU');
    };

    const parseChanges = (changesJson) => {
        try {
            if (!changesJson) return null;
            return JSON.parse(changesJson);
        } catch {
            return null;
        }
    };

    const formatJson = (obj) => {
        if (!obj) return '';
        try {
            return JSON.stringify(obj, null, 2);
        } catch {
            return String(obj);
        }
    };

    const getFieldDiff = (oldValue, newValue) => {
        if (!oldValue || !newValue) return [];
        const old = typeof oldValue === 'string' ? JSON.parse(oldValue) : oldValue;
        const newVal = typeof newValue === 'string' ? JSON.parse(newValue) : newValue;
        
        const diffs = [];
        const allKeys = new Set([...Object.keys(old || {}), ...Object.keys(newVal || {})]);
        
        allKeys.forEach(key => {
            const oldVal = old?.[key];
            const newValItem = newVal?.[key];
            
            if (oldVal !== newValItem) {
                diffs.push({
                    field: key,
                    old: oldVal,
                    new: newValItem
                });
            }
        });
        
        return diffs;
    };

    const openDetails = (log) => {
        setSelectedLog(log);
        setIsDetailsOpen(true);
    };

    const clearFilters = () => {
        setTableName('ALL');
        setAction('ALL');
        setUserID('');
        setStartDate('');
        setEndDate('');
        setPage(0);
    };

    return (
        <div className="container mx-auto p-6 space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-3xl font-bold">Аудит действий</h1>
            </div>

            {stats && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-green-600">Создано</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.creates || 0}</div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-blue-600">Обновлено</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.updates || 0}</div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-red-600">Удалено</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.deletes || 0}</div>
                        </CardContent>
                    </Card>
                </div>
            )}

            <Card>
                <CardHeader>
                    <CardTitle>Фильтры</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                        <Select value={action} onValueChange={setAction}>
                            <SelectTrigger>
                                <SelectValue placeholder="Действие" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все действия</SelectItem>
                                <SelectItem value="CREATE">CREATE</SelectItem>
                                <SelectItem value="UPDATE">UPDATE</SelectItem>
                                <SelectItem value="DELETE">DELETE</SelectItem>
                            </SelectContent>
                        </Select>

                        <Select value={tableName} onValueChange={setTableName}>
                            <SelectTrigger>
                                <SelectValue placeholder="Таблица" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все таблицы</SelectItem>
                                {tableNames.map(name => (
                                    <SelectItem key={name} value={name}>{name}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>

                        <Input
                            placeholder="ID пользователя"
                            type="number"
                            value={userID}
                            onChange={(e) => setUserID(e.target.value)}
                        />

                        <Input
                            type="datetime-local"
                            placeholder="Начальная дата"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                        />

                        <Input
                            type="datetime-local"
                            placeholder="Конечная дата"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                        />
                    </div>
                    <div className="flex gap-2 mt-4">
                        <Button onClick={clearFilters} variant="outline">
                            <Filter className="h-4 w-4 mr-2" />
                            Сбросить фильтры
                        </Button>
                    </div>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>Записи аудита ({totalElements})</CardTitle>
                </CardHeader>
                <CardContent>
                    {loading ? (
                        <div className="text-center py-8">Загрузка...</div>
                    ) : error ? (
                        <div className="text-center py-8 text-red-500">{error}</div>
                    ) : auditLogs.length === 0 ? (
                        <div className="text-center py-8">Записи аудита не найдены</div>
                    ) : (
                        <>
                            <div className="overflow-x-auto">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Дата</TableHead>
                                            <TableHead>Действие</TableHead>
                                            <TableHead>Таблица</TableHead>
                                            <TableHead>ID записи</TableHead>
                                            <TableHead>Пользователь</TableHead>
                                            <TableHead>IP</TableHead>
                                            <TableHead>Endpoint</TableHead>
                                            <TableHead>Изменения</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {auditLogs.map((log) => {
                                            const changes = parseChanges(log.changes);
                                            return (
                                                <TableRow key={log.logID}>
                                                    <TableCell className="text-sm">
                                                        {formatDate(log.logDate)}
                                                    </TableCell>
                                                    <TableCell>
                                                        <div className={`flex items-center gap-2 px-2 py-1 rounded ${getActionColor(log.action)}`}>
                                                            {getActionIcon(log.action)}
                                                            <span className="font-medium">{log.action}</span>
                                                        </div>
                                                    </TableCell>
                                                    <TableCell className="text-sm font-medium">{log.tableName}</TableCell>
                                                    <TableCell className="text-sm">{log.recordID}</TableCell>
                                                    <TableCell className="text-sm">
                                                        {log.userLogin || log.userID || '-'}
                                                    </TableCell>
                                                    <TableCell className="text-sm">{log.ipAddress || '-'}</TableCell>
                                                    <TableCell className="text-sm max-w-xs truncate" title={log.endpoint}>
                                                        {log.requestMethod} {log.endpoint}
                                                    </TableCell>
                                                    <TableCell className="text-sm max-w-md">
                                                        {changes ? (
                                                            <div className="flex items-center gap-2">
                                                                <Button
                                                                    variant="ghost"
                                                                    size="sm"
                                                                    onClick={() => openDetails(log)}
                                                                    className="h-8"
                                                                >
                                                                    <Eye className="h-4 w-4 mr-1" />
                                                                    Детали
                                                                </Button>
                                                            </div>
                                                        ) : (
                                                            '-'
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            );
                                        })}
                                    </TableBody>
                                </Table>
                            </div>
                            {totalPages > 1 && (
                                <div className="mt-4">
                                    <Pagination
                                        currentPage={page}
                                        totalPages={totalPages}
                                        onPageChange={setPage}
                                    />
                                </div>
                            )}
                        </>
                    )}
                </CardContent>
            </Card>

            {/* Модальное окно с деталями изменений */}
            <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
                <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
                    <DialogHeader>
                        <DialogTitle>Детали изменений</DialogTitle>
                    </DialogHeader>
                    {selectedLog && (
                        <div className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h4 className="font-semibold mb-2">Общая информация</h4>
                                    <div className="space-y-1 text-sm">
                                        <div><strong>Действие:</strong> {selectedLog.action}</div>
                                        <div><strong>Таблица:</strong> {selectedLog.tableName}</div>
                                        <div><strong>ID записи:</strong> {selectedLog.recordID}</div>
                                        <div><strong>Пользователь:</strong> {selectedLog.userLogin || selectedLog.userID || '-'}</div>
                                        <div><strong>Дата:</strong> {formatDate(selectedLog.logDate)}</div>
                                        <div><strong>IP:</strong> {selectedLog.ipAddress || '-'}</div>
                                        <div><strong>Endpoint:</strong> {selectedLog.requestMethod} {selectedLog.endpoint}</div>
                                    </div>
                                </div>
                                <div>
                                    <h4 className="font-semibold mb-2">Техническая информация</h4>
                                    <div className="space-y-1 text-sm">
                                        <div><strong>User-Agent:</strong></div>
                                        <div className="text-xs text-gray-600 break-words">{selectedLog.userAgent || '-'}</div>
                                    </div>
                                </div>
                            </div>
                            
                            {parseChanges(selectedLog.changes) && (
                                <div>
                                    <h4 className="font-semibold mb-2">Изменения данных</h4>
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <div className="bg-red-50 p-3 rounded border border-red-200">
                                                <div className="font-semibold text-red-700 mb-2">Старое значение:</div>
                                                <pre className="text-xs overflow-auto max-h-96 bg-white p-2 rounded">
                                                    {formatJson(parseChanges(selectedLog.changes)?.oldValue)}
                                                </pre>
                                            </div>
                                        </div>
                                        <div>
                                            <div className="bg-green-50 p-3 rounded border border-green-200">
                                                <div className="font-semibold text-green-700 mb-2">Новое значение:</div>
                                                <pre className="text-xs overflow-auto max-h-96 bg-white p-2 rounded">
                                                    {formatJson(parseChanges(selectedLog.changes)?.newValue)}
                                                </pre>
                                            </div>
                                        </div>
                                    </div>
                                    
                                    {selectedLog.action === 'UPDATE' && (
                                        <div className="mt-4">
                                            <h4 className="font-semibold mb-2">Измененные поля:</h4>
                                            <div className="space-y-2">
                                                {getFieldDiff(
                                                    parseChanges(selectedLog.changes)?.oldValue,
                                                    parseChanges(selectedLog.changes)?.newValue
                                                ).map((diff, idx) => (
                                                    <div key={idx} className="bg-gray-50 p-2 rounded text-sm">
                                                        <div className="font-medium">{diff.field}:</div>
                                                        <div className="text-red-600 line-through">{String(diff.old ?? 'null')}</div>
                                                        <div className="text-green-600">→ {String(diff.new ?? 'null')}</div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}

