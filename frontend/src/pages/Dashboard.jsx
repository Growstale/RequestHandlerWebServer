import React, { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { getDashboardStats } from '@/api/analyticsApi';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { 
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, 
    PieChart, Pie, Cell, LineChart, Line, AreaChart, Area 
} from 'recharts';
import { 
    Activity, CheckCircle2, Clock, AlertTriangle, 
    Briefcase, TrendingUp, Users, Printer 
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName } from '@/lib/displayNames';

// Цвета для графиков
const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];
const STATUS_COLORS = {
    'In work': '#3b82f6', // blue
    'Done': '#22c55e',    // green
    'Closed': '#64748b'   // slate
};

export default function Dashboard() {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchStats = async () => {
            try {
                const res = await getDashboardStats();
                setStats(res.data);
            } catch (err) {
                console.error(err);
                setError("Не удалось загрузить аналитику.");
            } finally {
                setLoading(false);
            }
        };
        fetchStats();
    }, []);

    if (loading) {
        return (
            <div className="flex h-[80vh] items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
            </div>
        );
    }

    if (error) {
        return <div className="p-8 text-red-600 text-center">{error}</div>;
    }

    // Подготовка данных для PieChart (по статусам)
    const statusData = stats.requestsByStatus.map(item => ({
        name: item.name,
        value: item.value,
        color: STATUS_COLORS[item.name] || '#94a3b8'
    }));

    const urgencyData = stats.requestsByUrgency.map(item => ({
        ...item,
        name: getUrgencyDisplayName(item.name)
    }));

    const handlePrint = () => {
        window.print();
    };

    return (
        <main className="container mx-auto p-6 space-y-6">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-3xl font-bold tracking-tight">Дашборд</h1>
                <Button onClick={handlePrint} variant="outline" className="no-print gap-2">
                    <Printer className="h-4 w-4" />
                    Печать отчета
                </Button>
            </div>

            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                <StatsCard 
                    title="Всего заявок" 
                    value={stats.totalRequests} 
                    icon={Briefcase} 
                    description="За все время"
                />
                <StatsCard 
                    title="В работе" 
                    value={stats.activeRequests} 
                    icon={Activity} 
                    className="text-blue-600"
                    description="Требуют внимания"
                />
                <StatsCard 
                    title="Просрочено" 
                    value={stats.overdueRequests} 
                    icon={AlertTriangle} 
                    className="text-red-600"
                    description="Срыв сроков"
                />
                <StatsCard 
                    title="Выполнено" 
                    value={stats.completedRequests} 
                    icon={CheckCircle2} 
                    className="text-green-600"
                    description="Успешно завершены"
                />
            </div>

            {/* --- 2. MAIN CHARTS --- */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
                
                {/* График тренда (Линейный) */}
                <Card className="col-span-4">
                    <CardHeader>
                        <CardTitle>Динамика заявок</CardTitle>
                        <CardDescription>Количество созданных заявок за последние 7 дней</CardDescription>
                    </CardHeader>
                    <CardContent className="pl-2">
                        <div className="h-[300px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={stats.requestsLast7Days}>
                                    <defs>
                                        <linearGradient id="colorCnt" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8}/>
                                            <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="date" />
                                    <YAxis allowDecimals={false} />
                                    <RechartsTooltip 
                                        contentStyle={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #e2e8f0' }}
                                    />
                                    <Area type="monotone" dataKey="count" stroke="#3b82f6" fillOpacity={1} fill="url(#colorCnt)" name="Заявки" />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>

                {/* Круговая диаграмма по Срочности */}
                <Card className="col-span-3">
                    <CardHeader>
                        <CardTitle>Распределение по срочности</CardTitle>
                        <CardDescription>Доли заявок по категориям важности</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="h-[300px] flex items-center justify-center">
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={urgencyData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={80}
                                        paddingAngle={5}
                                        dataKey="value"
                                    >
                                        {urgencyData.map((entry, index) => ( 
                                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                        ))}
                                    </Pie>
                                    <RechartsTooltip />
                                </PieChart>
                            </ResponsiveContainer>
                        </div>
                        <div className="flex justify-center gap-4 text-sm text-gray-500 flex-wrap">
                            {urgencyData.map((entry, index) => (
                                <div key={index} className="flex items-center gap-1">
                                    <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[index % COLORS.length] }}></div>
                                    <span>{entry.name}: {entry.value}</span>
                                </div>
                            ))}
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* --- 3. BOTTOM CHARTS --- */}
            <div className="grid gap-4 md:grid-cols-2">
                
                {/* Топ категорий работ (Бар) */}
                <Card>
                    <CardHeader>
                        <CardTitle>Топ категорий работ</CardTitle>
                        <CardDescription>Какие виды работ требуются чаще всего</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="h-[300px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart layout="vertical" data={stats.requestsByWorkCategory} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                    <XAxis type="number" allowDecimals={false} />
                                    <YAxis dataKey="name" type="category" width={100} tick={{fontSize: 12}} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#8884d8" radius={[0, 4, 4, 0]} name="Заявки" />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>

                {/* Топ подрядчиков (Таблица/Список) */}
                <Card>
                    <CardHeader>
                        <CardTitle>Лидеры по выполнению</CardTitle>
                        <CardDescription>Топ-5 подрядчиков по количеству закрытых заявок</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-4">
                            {stats.topContractors.length === 0 ? (
                                <p className="text-muted-foreground text-sm">Данных пока нет.</p>
                            ) : (
                                stats.topContractors.map((contractor, i) => (
                                    <div key={i} className="flex items-center justify-between">
                                        <div className="flex items-center gap-3">
                                            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-muted">
                                                <Users className="h-5 w-5 text-gray-500" />
                                            </div>
                                            <div className="space-y-1">
                                                <p className="text-sm font-medium leading-none">{contractor.name}</p>
                                                <p className="text-xs text-muted-foreground">Подрядчик</p>
                                            </div>
                                        </div>
                                        <div className="font-bold text-sm">
                                            {contractor.completedCount} выполнено
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </CardContent>
                </Card>
            </div>
        </main>
    );
}

function StatsCard({ title, value, icon: Icon, description, className }) {
    return (
        <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                    {title}
                </CardTitle>
                <Icon className={cn("h-4 w-4 text-muted-foreground", className)} />
            </CardHeader>
            <CardContent>
                <div className={cn("text-2xl font-bold", className)}>{value}</div>
                <p className="text-xs text-muted-foreground">
                    {description}
                </p>
            </CardContent>
        </Card>
    );
}