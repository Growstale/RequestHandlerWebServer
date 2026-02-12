import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { auditApi } from '@/api/auditApi';
import { logger } from '@/lib/logger';
import { Filter, Plus, Edit, Trash2 } from 'lucide-react';
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
    const [tableName, setTableName] = useState('');
    const [action, setAction] = useState('ALL');
    const [userID, setUserID] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    const [stats, setStats] = useState(null);

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
                ...(tableName && { tableName }),
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

    const clearFilters = () => {
        setTableName('');
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

                        <Input
                            placeholder="Таблица"
                            value={tableName}
                            onChange={(e) => setTableName(e.target.value)}
                        />

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
                                                            <details className="cursor-pointer">
                                                                <summary className="text-blue-600 hover:underline">
                                                                    Показать изменения
                                                                </summary>
                                                                <div className="mt-2 p-2 bg-gray-50 rounded text-xs">
                                                                    <div><strong>Старое:</strong> {JSON.stringify(changes.oldValue, null, 2)}</div>
                                                                    <div className="mt-2"><strong>Новое:</strong> {JSON.stringify(changes.newValue, null, 2)}</div>
                                                                </div>
                                                            </details>
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
        </div>
    );
}

