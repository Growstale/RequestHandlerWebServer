import React, { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { getDashboardStats } from '@/api/analyticsApi';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { 
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, 
    PieChart, Pie, Cell, AreaChart, Area 
} from 'recharts';
import { 
    Activity, CheckCircle2, AlertTriangle, 
    Briefcase, Printer, Download, Clock, ShieldCheck, TrendingUp
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName } from '@/lib/displayNames';
import * as XLSX from 'xlsx';

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];
const STATUS_COLORS = {
    'In work': '#3b82f6',
    'Done': '#22c55e',
    'Closed': '#64748b'
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

    const handleExportExcel = () => {
        if (!stats) return;

        const workbook = XLSX.utils.book_new();

        const summaryData = [
            ["Показатель", "Значение"],
            ["Всего заявок", stats.totalRequests],
            ["В работе", stats.activeRequests],
            ["Просрочено", stats.overdueRequests],
            ["Выполнено", stats.completedRequests],
            ["Среднее время выполнения (дней)", stats.averageCompletionTimeDays?.toFixed(1)],
            ["Соблюдение SLA (%)", stats.slaCompliancePercent?.toFixed(1)]
        ];
        const summarySheet = XLSX.utils.aoa_to_sheet(summaryData);
        XLSX.utils.book_append_sheet(workbook, summarySheet, "Сводка");

        const contractorsData = [
            ["Имя подрядчика", "Выполнено заявок"],
            ...stats.topContractors.map(c => [c.name, c.completedCount])
        ];
        const contractorsSheet = XLSX.utils.aoa_to_sheet(contractorsData);
        XLSX.utils.book_append_sheet(workbook, contractorsSheet, "Топ подрядчиков");

        const dateStr = new Date().toLocaleDateString('ru-RU').replace(/\./g, '-');
        XLSX.writeFile(workbook, `Otchet_Dashboard_${dateStr}.xlsx`);
    };

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
                <div className="flex gap-2 no-print">
                    <Button onClick={handleExportExcel} variant="outline" className="gap-2 bg-green-50 text-green-700 border-green-200 hover:bg-green-100 hover:text-green-800">
                        <Download className="h-4 w-4" />
                        Скачать Excel
                    </Button>
                    <Button onClick={handlePrint} variant="outline" className="gap-2">
                        <Printer className="h-4 w-4" />
                        Печать
                    </Button>
                </div>
            </div>

            {/* Карточки KPI */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
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
                    description="Активные"
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
                    description="Завершено"
                />
                <StatsCard 
                    title="Среднее время" 
                    value={stats.averageCompletionTimeDays ? `${stats.averageCompletionTimeDays.toFixed(1)} дн.` : "—"} 
                    icon={Clock} 
                    className="text-purple-600"
                    description="Скорость решения"
                />
                <StatsCard 
                    title="SLA" 
                    value={stats.slaCompliancePercent ? `${stats.slaCompliancePercent.toFixed(0)}%` : "—"} 
                    icon={ShieldCheck} 
                    className={stats.slaCompliancePercent >= 90 ? "text-green-600" : "text-orange-500"}
                    description="Соблюдение сроков"
                />
            </div>

            {/* Ряд 1: Динамика и Круговая диаграмма */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
                <Card className="col-span-4">
                    <CardHeader>
                        <CardTitle>Динамика заявок (7 дней)</CardTitle>
                        <CardDescription>Количество созданных заявок</CardDescription>
                    </CardHeader>
                    <CardContent className="pl-2">
                        <div className="h-[300px] w-full min-h-[300px]"> 
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
                                    <RechartsTooltip contentStyle={{ backgroundColor: 'white', borderRadius: '8px', border: '1px solid #e2e8f0' }}/>
                                    <Area type="monotone" dataKey="count" stroke="#3b82f6" fillOpacity={1} fill="url(#colorCnt)" name="Заявки" />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>

                <Card className="col-span-3">
                    <CardHeader>
                        <CardTitle>Срочность</CardTitle>
                        <CardDescription>Распределение по важности</CardDescription>
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
                        <div className="flex justify-center gap-3 text-xs text-gray-500 flex-wrap">
                            {urgencyData.map((entry, index) => (
                                <div key={index} className="flex items-center gap-1">
                                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: COLORS[index % COLORS.length] }}></div>
                                    <span>{entry.name}: {entry.value}</span>
                                </div>
                            ))}
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Ряд 2: Загрузка подрядчиков и Топ проблемных магазинов (НОВОЕ) */}
            <div className="grid gap-4 md:grid-cols-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Текущая загрузка подрядчиков</CardTitle>
                        <CardDescription>Количество активных заявок ("В работе")</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="h-[300px] w-full min-h-[300px]"> 
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart layout="vertical" data={stats.contractorWorkload} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                    <XAxis type="number" allowDecimals={false} />
                                    <YAxis dataKey="name" type="category" width={100} tick={{fontSize: 12}} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#f59e0b" radius={[0, 4, 4, 0]} name="В работе" barSize={20} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>Топ проблемных магазинов</CardTitle>
                        <CardDescription>Магазины с наибольшим количеством заявок</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="h-[300px] w-full min-h-[300px]"> 
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={stats.topProblemShops} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="name" tick={{fontSize: 11}} interval={0} angle={-15} textAnchor="end" height={60}/>
                                    <YAxis allowDecimals={false} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#ef4444" radius={[4, 4, 0, 0]} name="Заявки" barSize={40} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Ряд 3: Виды работ и Лидеры (Сжато) */}
            <div className="grid gap-4 md:grid-cols-2">
                <Card>
                    <CardHeader>
                        <CardTitle>Топ категорий работ</CardTitle>
                        <CardDescription>Самые частые причины обращений</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="h-[250px] w-full"> 
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart layout="vertical" data={stats.requestsByWorkCategory} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                    <XAxis type="number" allowDecimals={false} />
                                    <YAxis dataKey="name" type="category" width={120} tick={{fontSize: 11}} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#8884d8" radius={[0, 4, 4, 0]} name="Заявки" barSize={15} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>Лидеры по продуктивности</CardTitle>
                        <CardDescription>Выполнено заявок (Топ 5)</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="space-y-4">
                            {stats.topContractors.map((contractor, i) => (
                                <div key={i} className="flex items-center justify-between border-b pb-2 last:border-0 last:pb-0">
                                    <div className="flex items-center gap-3">
                                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-50 text-blue-600 font-bold text-xs">
                                            {i + 1}
                                        </div>
                                        <p className="text-sm font-medium leading-none">{contractor.name}</p>
                                    </div>
                                    <div className="font-bold text-sm">
                                        {contractor.completedCount} <span className="text-gray-400 font-normal text-xs">закрыто</span>
                                    </div>
                                </div>
                            ))}
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