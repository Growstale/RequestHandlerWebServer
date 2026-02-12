import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { logApi } from '@/api/logApi';
import { logger } from '@/lib/logger';
import { AlertTriangle, Info, AlertCircle, Search, Filter, Download } from 'lucide-react';
import Pagination from '@/components/Pagination';

export default function Logs() {
    const [logs, setLogs] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [page, setPage] = useState(0);
    const [size] = useState(50);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    
    // Filters
    const [logLevel, setLogLevel] = useState('ALL');
    const [loggerName, setLoggerName] = useState('');
    const [userID, setUserID] = useState('');
    const [startDate, setStartDate] = useState('');
    const [endDate, setEndDate] = useState('');

    const [stats, setStats] = useState(null);

    useEffect(() => {
        fetchLogs();
        fetchStats();
    }, [page, logLevel, loggerName, userID, startDate, endDate]);

    const fetchLogs = async () => {
        try {
            setLoading(true);
            const params = {
                page,
                size,
                ...(logLevel && logLevel !== 'ALL' && { logLevel }),
                ...(loggerName && { loggerName }),
                ...(userID && { userID: parseInt(userID) }),
                ...(startDate && { startDate }),
                ...(endDate && { endDate })
            };
            const response = await logApi.getLogs(params);
            setLogs(response.data.content);
            setTotalPages(response.data.totalPages);
            setTotalElements(response.data.totalElements);
        } catch (err) {
            logger.error('Logs', err);
            setError('Не удалось загрузить логи');
        } finally {
            setLoading(false);
        }
    };

    const fetchStats = async () => {
        try {
            const params = {};
            if (startDate) params.startDate = startDate;
            if (endDate) params.endDate = endDate;
            const response = await logApi.getLogStats(params);
            setStats(response.data);
        } catch (err) {
            logger.error('Logs stats', err);
        }
    };

    const getLogLevelIcon = (level) => {
        switch (level) {
            case 'ERROR':
                return <AlertTriangle className="h-4 w-4 text-red-500" />;
            case 'WARN':
                return <AlertCircle className="h-4 w-4 text-yellow-500" />;
            case 'INFO':
                return <Info className="h-4 w-4 text-blue-500" />;
            default:
                return <Info className="h-4 w-4 text-gray-500" />;
        }
    };

    const getLogLevelColor = (level) => {
        switch (level) {
            case 'ERROR':
                return 'text-red-600 bg-red-50';
            case 'WARN':
                return 'text-yellow-600 bg-yellow-50';
            case 'INFO':
                return 'text-blue-600 bg-blue-50';
            default:
                return 'text-gray-600 bg-gray-50';
        }
    };

    const formatDate = (dateString) => {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleString('ru-RU');
    };

    const clearFilters = () => {
        setLogLevel('ALL');
        setLoggerName('');
        setUserID('');
        setStartDate('');
        setEndDate('');
        setPage(0);
    };

    return (
        <div className="container mx-auto p-6 space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-3xl font-bold">Логи приложения</h1>
            </div>

            {stats && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-red-600">Ошибки</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.errors || 0}</div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-yellow-600">Предупреждения</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.warnings || 0}</div>
                        </CardContent>
                    </Card>
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm font-medium text-blue-600">Информация</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="text-2xl font-bold">{stats.info || 0}</div>
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
                        <Select value={logLevel} onValueChange={setLogLevel}>
                            <SelectTrigger>
                                <SelectValue placeholder="Уровень лога" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">Все уровни</SelectItem>
                                <SelectItem value="ERROR">ERROR</SelectItem>
                                <SelectItem value="WARN">WARN</SelectItem>
                                <SelectItem value="INFO">INFO</SelectItem>
                                <SelectItem value="DEBUG">DEBUG</SelectItem>
                            </SelectContent>
                        </Select>

                        <Input
                            placeholder="Имя логгера"
                            value={loggerName}
                            onChange={(e) => setLoggerName(e.target.value)}
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
                    <CardTitle>Логи ({totalElements})</CardTitle>
                </CardHeader>
                <CardContent>
                    {loading ? (
                        <div className="text-center py-8">Загрузка...</div>
                    ) : error ? (
                        <div className="text-center py-8 text-red-500">{error}</div>
                    ) : logs.length === 0 ? (
                        <div className="text-center py-8">Логи не найдены</div>
                    ) : (
                        <>
                            <div className="overflow-x-auto">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Дата</TableHead>
                                            <TableHead>Уровень</TableHead>
                                            <TableHead>Логгер</TableHead>
                                            <TableHead>Сообщение</TableHead>
                                            <TableHead>Пользователь</TableHead>
                                            <TableHead>IP</TableHead>
                                            <TableHead>Endpoint</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {logs.map((log) => (
                                            <TableRow key={log.logID}>
                                                <TableCell className="text-sm">
                                                    {formatDate(log.logDate)}
                                                </TableCell>
                                                <TableCell>
                                                    <div className={`flex items-center gap-2 px-2 py-1 rounded ${getLogLevelColor(log.logLevel)}`}>
                                                        {getLogLevelIcon(log.logLevel)}
                                                        <span className="font-medium">{log.logLevel}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell className="text-sm">{log.loggerName}</TableCell>
                                                <TableCell className="text-sm max-w-md truncate" title={log.message}>
                                                    {log.message}
                                                </TableCell>
                                                <TableCell className="text-sm">
                                                    {log.userLogin || log.userID || '-'}
                                                </TableCell>
                                                <TableCell className="text-sm">{log.ipAddress || '-'}</TableCell>
                                                <TableCell className="text-sm max-w-xs truncate" title={log.endpoint}>
                                                    {log.requestMethod} {log.endpoint}
                                                </TableCell>
                                            </TableRow>
                                        ))}
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

