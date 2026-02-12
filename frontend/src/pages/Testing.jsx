import React, { useState, useEffect } from 'react';
import { 
    getTestConfig, setCheckInterval, setReminderCron, 
    updateRequestDate, triggerCheckNow, triggerRemindNow 
} from '@/api/testApi';
import { getRequests } from '@/api/requestApi';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import { Play, Save, Clock, CalendarDays, RefreshCw } from 'lucide-react';

export default function Testing() {
    const [config, setConfig] = useState({ checkInterval: 30000, reminderCron: '' });
    const [requests, setRequests] = useState([]);
    const [selectedRequest, setSelectedRequest] = useState('');
    const [newDate, setNewDate] = useState('');
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState('');

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        try {
            const [confRes, reqRes] = await Promise.all([
                getTestConfig(),
                getRequests({ 
                    size: 1000, 
                    sortConfig: [{ field: 'requestID', direction: 'desc' }] 
                })
            ]);            
            setConfig(confRes.data);
            setRequests(reqRes.data.content);
        } catch (e) {
            console.error(e);
        }
    };

    const handleSaveInterval = async () => {
        try {
            await setCheckInterval(parseInt(config.checkInterval));
            setMessage('Интервал обновлен');
        } catch (e) {
            setMessage('Ошибка обновления интервала');
        }
    };

    const handleSaveCron = async () => {
        try {
            await setReminderCron(config.reminderCron);
            setMessage('Cron обновлен');
        } catch (e) {
            setMessage('Ошибка обновления Cron');
        }
    };

    const handleChangeDate = async () => {
        if (!selectedRequest || !newDate) return;

        const selectedDateObj = new Date(newDate);
        const now = new Date();

        if (selectedDateObj > now) {
            setMessage("❌ Ошибка: Нельзя устанавливать дату в будущем!");
            return;
        }

        try {
            const isoDate = new Date(newDate).toISOString().slice(0, 19); 
            await updateRequestDate(selectedRequest, isoDate);
            setMessage(`✅ Дата заявки #${selectedRequest} успешно изменена`);
            loadData();
        } catch (e) {
            const serverMsg = e.response?.data?.message || e.response?.data || "Ошибка изменения даты";
            setMessage(`❌ ${serverMsg}`);
        }
    };

    return (
        <main className="container mx-auto p-6 space-y-6">
            <h1 className="text-3xl font-bold mb-6">⚙️ Панель тестирования</h1>
            
            {message && <div className="bg-blue-100 text-blue-800 p-3 rounded mb-4">{message}</div>}

            <div className="grid md:grid-cols-2 gap-6">
                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <Clock className="h-5 w-5"/> Проверка просрочек
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <div>
                            <Label>Интервал проверки (мс)</Label>
                            <div className="flex gap-2 mt-1">
                                <Input 
                                    type="datetime-local" 
                                    onChange={e => setNewDate(e.target.value)}
                                    max={new Date().toISOString().slice(0, 16)} 
                                />
                                <Button onClick={handleSaveInterval}><Save className="h-4 w-4"/></Button>
                            </div>
                            <p className="text-xs text-gray-500 mt-1">Текущий: {config.checkInterval / 1000} сек</p>
                        </div>
                        <Button variant="secondary" className="w-full" onClick={() => triggerCheckNow().then(() => setMessage('Проверка запущена!'))}>
                            <Play className="mr-2 h-4 w-4"/> Запустить проверку принудительно
                        </Button>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <CalendarDays className="h-5 w-5"/> Ежедневные напоминания
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <div>
                            <Label>CRON Расписание</Label>
                            <div className="flex gap-2 mt-1">
                                <Input 
                                    value={config.reminderCron} 
                                    onChange={e => setConfig({...config, reminderCron: e.target.value})}
                                />
                                <Button onClick={handleSaveCron}><Save className="h-4 w-4"/></Button>
                            </div>
                            <p className="text-xs text-gray-500 mt-1">Пример: 0/10 * * * * * (каждые 10 сек)</p>
                        </div>
                        <Button variant="secondary" className="w-full" onClick={() => triggerRemindNow().then(() => setMessage('Напоминания отправлены!'))}>
                            <Play className="mr-2 h-4 w-4"/> Отправить напоминания сейчас
                        </Button>
                    </CardContent>
                </Card>

                <Card className="md:col-span-2">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <RefreshCw className="h-5 w-5"/> Изменение даты заявки (Тест просрочки)
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <div className="grid md:grid-cols-2 gap-4">
                            <div>
                                <Label>Выберите заявку</Label>
                                <Select onValueChange={setSelectedRequest}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="Заявка..." />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {requests.map(r => (
                                            <SelectItem key={r.requestID} value={r.requestID.toString()}>
                                                #{r.requestID} {r.shopName} ({new Date(r.createdAt).toLocaleDateString()})
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <Label>Новая дата создания</Label>
                                <Input 
                                    type="datetime-local" 
                                    onChange={e => setNewDate(e.target.value)}
                                />
                            </div>
                        </div>
                        <Button onClick={handleChangeDate} disabled={!selectedRequest || !newDate}>
                            Применить новую дату
                        </Button>
                    </CardContent>
                </Card>
            </div>
        </main>
    );
}