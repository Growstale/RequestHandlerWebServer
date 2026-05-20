import React, { useEffect, useState, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import api from '@/api/axios';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { 
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, 
    PieChart, Pie, Cell, AreaChart, Area 
} from 'recharts';
import { 
    Activity, CheckCircle2, AlertTriangle, 
    Briefcase, Printer, Download, Clock, ShieldCheck, CalendarRange, TrendingUp, Info
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { getUrgencyDisplayName, getStatusDisplayName } from '@/lib/displayNames';
import * as XLSX from 'xlsx';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"

const COLORS =['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884d8'];

export default function Dashboard() {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [period, setPeriod] = useState('month'); 
    const [customStart, setCustomStart] = useState('');
    const [customEnd, setCustomEnd] = useState('');

    const dateRanges = useMemo(() => {
        const today = new Date();
        const end = today.toISOString().split('T')[0];
        let start = '';

        const startObj = new Date(today);
        switch (period) {
            case 'today': start = end; break;
            case 'week': startObj.setDate(today.getDate() - 7); start = startObj.toISOString().split('T')[0]; break;
            case 'month': startObj.setMonth(today.getMonth() - 1); start = startObj.toISOString().split('T')[0]; break;
            case 'quarter': startObj.setMonth(today.getMonth() - 3); start = startObj.toISOString().split('T')[0]; break;
            case 'half_year': startObj.setMonth(today.getMonth() - 6); start = startObj.toISOString().split('T')[0]; break;
            case 'year': startObj.setFullYear(today.getFullYear() - 1); start = startObj.toISOString().split('T')[0]; break;
            case 'all': start = '2000-01-01'; break;
            case 'custom': start = customStart; break;
            default: startObj.setMonth(today.getMonth() - 1); start = startObj.toISOString().split('T')[0]; break;
        }
        return { startDate: start, endDate: period === 'custom' ? customEnd : end };
    }, [period, customStart, customEnd]);

    useEffect(() => {
        const fetchStats = async () => {
            if (period === 'custom' && (!customStart || !customEnd)) return; 

            setLoading(true);
            setError(null);
            try {
                let url = '/api/analytics/stats';
                if (dateRanges.startDate && dateRanges.endDate) {
                    url += `?startDate=${dateRanges.startDate}&endDate=${dateRanges.endDate}`;
                }
                const res = await api.get(url);
                setStats(res.data);
            } catch (err) {
                console.error(err);
                setError("Не удалось загрузить аналитику. Проверьте подключение к серверу.");
            } finally {
                setLoading(false);
            }
        };
        fetchStats();
    }, [dateRanges, period, customStart, customEnd]);

    const resolutionRate = stats && stats.totalRequests > 0 
        ? ((stats.completedRequests / stats.totalRequests) * 100).toFixed(0) 
        : "0";

    const handleExportExcel = () => {
        if (!stats) return;

        const workbook = XLSX.utils.book_new();

        let periodTitle = "За выбранный период";
        if (period === 'all') periodTitle = "За всё время";
        else if (period === 'today') periodTitle = "За сегодня";
        else if (customStart && customEnd) periodTitle = `С ${customStart} по ${customEnd}`;

        const setColWidths = (sheet, widths) => {
            sheet['!cols'] = widths.map(w => ({ wch: w }));
        };

        const summaryData = [
            ["ОТЧЕТ ПО ЗАЯВКАМ: MART INN FOOD"],
            [`Период отчета: ${periodTitle}`],
            [`Дата генерации: ${new Date().toLocaleString('ru-RU')}`],
            [], 
            ["--- КЛЮЧЕВЫЕ ПОКАЗАТЕЛИ (KPI) ---", ""],
            ["Всего заявок (создано)", stats.totalRequests],
            ["В работе (текущий бэклог)", stats.activeRequests],
            ["Просрочено (текущий долг)", stats.overdueRequests],
            ["Выполнено (за период)", stats.completedRequests],
            ["Коэффициент закрытия", `${resolutionRate}%`],
            ["Среднее время выполнения", `${stats.averageCompletionTimeDays?.toFixed(1) || 0} дней`],
            ["Соблюдение SLA", `${stats.slaCompliancePercent?.toFixed(1) || 0}%`],
            [],
            ["--- ПРОБЛЕМНЫЕ МАГАЗИНЫ (ПРОСРОЧКИ) ---", ""],
            ["Магазин", "Кол-во просроченных"],
            ...stats.worstShops.map(s => [s.name, s.value]),
            ...(stats.worstShops.length === 0 ? [["Просрочек нет", "-"]] : []),
            [],
            ["--- АНТИРЕЙТИНГ ПОДРЯДЧИКОВ ---", ""],
            ["Имя подрядчика", "Кол-во просроченных"],
            ...stats.worstContractors.map(c => [c.name, c.value]),
            ...(stats.worstContractors.length === 0 ? [["Просрочек нет", "-"]] : []),
        ];
        const summarySheet = XLSX.utils.aoa_to_sheet(summaryData);
        setColWidths(summarySheet, [40, 20]); 
        XLSX.utils.book_append_sheet(workbook, summarySheet, "Главная сводка");

        const analyticsData = [
            ["--- СТАТУСЫ ЗАЯВОК ---", ""],
            ...stats.requestsByStatus.map(s => [getStatusDisplayName(s.name) || s.name, s.value]),
            [],
            ["--- РАСПРЕДЕЛЕНИЕ ПО СРОЧНОСТИ ---", ""],
            ...stats.requestsByUrgency.map(u => [getUrgencyDisplayName(u.name) || u.name, u.value]),
            [],
            ["--- ТОП ВИДОВ РАБОТ ---", ""],
            ...stats.requestsByWorkCategory.map(w => [w.name, w.value])
        ];
        const analyticsSheet = XLSX.utils.aoa_to_sheet(analyticsData);
        setColWidths(analyticsSheet, [40, 15]);
        XLSX.utils.book_append_sheet(workbook, analyticsSheet, "Аналитика");

        const dynamicsData = [
            ["--- ДИНАМИКА СОЗДАНИЯ ЗАЯВОК ---", ""],
            ["Период", "Кол-во новых заявок"],
            ...stats.requestsLast7Days.map(d => [d.date, d.count]),
            [],
            ["--- ЛИДЕРЫ ПО ПРОДУКТИВНОСТИ ---", ""],
            ["Имя подрядчика", "Закрыто заявок"],
            ...stats.topContractors.map(c => [c.name, c.completedCount]),
            ...(stats.topContractors.length === 0 ? [["Нет выполненных заявок", "-"]] : []),
            [],
            ["--- ТЕКУЩАЯ НАГРУЗКА (БЭКЛОГ) ---", ""],
            ["Имя подрядчика", "Заявок в работе"],
            ...stats.contractorWorkload.map(c => [c.name, c.value])
        ];
        const dynamicsSheet = XLSX.utils.aoa_to_sheet(dynamicsData);
        setColWidths(dynamicsSheet, [40, 25]);
        XLSX.utils.book_append_sheet(workbook, dynamicsSheet, "Динамика и Подрядчики");

        const dateStr = new Date().toLocaleDateString('ru-RU').replace(/\./g, '-');
        XLSX.writeFile(workbook, `Otchet_Dashboard_${dateStr}.xlsx`);
    };

    const handlePrint = () => {
        window.print();
    };

    if (loading) {
        return (
            <div className="flex h-[80vh] items-center justify-center">
                <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
            </div>
        );
    }

    if (error) {
        return <div className="p-8 text-red-600 text-center bg-red-50 m-6 rounded-lg">{error}</div>;
    }

    const urgencyData = stats?.requestsByUrgency.map(item => ({
        ...item,
        name: getUrgencyDisplayName(item.name)
    })) || [];

    return (
        <TooltipProvider delayDuration={200}>
            <main className="container mx-auto p-6 space-y-6">
                <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-6">
                    <h1 className="text-3xl font-bold tracking-tight">Дашборд</h1>
                    
                    <div className="flex flex-col sm:flex-row flex-wrap items-start sm:items-center bg-white border rounded-lg shadow-sm no-print p-1 gap-1 max-w-full">
                        <div className="flex items-center w-full sm:w-auto">
                            <div className="pl-2 pr-1 hidden sm:flex items-center justify-center">
                                <CalendarRange className="h-4 w-4 text-gray-500" />
                            </div>
                            <Select value={period} onValueChange={setPeriod}>
                                <SelectTrigger className="w-full sm:w-[150px] border-none shadow-none focus:ring-0 focus:ring-offset-0 bg-transparent h-8">
                                    <SelectValue placeholder="Выберите период" />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="today">За сегодня</SelectItem>
                                    <SelectItem value="week">За 7 дней</SelectItem>
                                    <SelectItem value="month">За месяц</SelectItem>
                                    <SelectItem value="quarter">За квартал</SelectItem>
                                    <SelectItem value="half_year">За полгода</SelectItem>
                                    <SelectItem value="year">За год</SelectItem>
                                    <SelectItem value="all">За всё время</SelectItem>
                                    <SelectItem value="custom">Свой период...</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                        
                        {period === 'custom' && (
                            <div className="flex items-center gap-2 px-1 sm:px-2 sm:border-l border-gray-100 pb-1 sm:pb-0 w-full sm:w-auto">
                                <Input 
                                    type="date" 
                                    value={customStart} 
                                    onChange={e => setCustomStart(e.target.value)} 
                                    className="h-8 text-xs flex-1 sm:w-[125px] border-gray-200 focus-visible:ring-1" 
                                />
                                <span className="text-gray-400 text-xs hidden sm:inline">—</span>
                                <Input 
                                    type="date" 
                                    value={customEnd} 
                                    onChange={e => setCustomEnd(e.target.value)} 
                                    className="h-8 text-xs flex-1 sm:w-[125px] border-gray-200 focus-visible:ring-1" 
                                />
                            </div>
                        )}
                    </div>

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
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
                    <StatsCard 
                        title="Создано заявок" 
                        value={stats.totalRequests} 
                        icon={Briefcase} 
                        description="За период создания"
                        tooltipText="Показывает объем новой работы, поступившей в систему (дата создания попадает в выбранный период)."
                    />
                    <StatsCard 
                        title="В работе (Всего)" 
                        value={stats.activeRequests} 
                        icon={Activity} 
                        className="text-blue-600"
                        description="Текущий бэклог"
                        tooltipText="Текущая очередь задач. Сколько заявок прямо сейчас находятся в статусе 'В работе'. Фильтр по датам на этот показатель не влияет."
                    />
                    <StatsCard 
                        title="Выполнено" 
                        value={stats.completedRequests} 
                        icon={CheckCircle2} 
                        className="text-green-600"
                        description="За период закрытия"
                        tooltipText="Количество заявок, которые были окончательно проверены и переведены в статус 'Закрыта' (в Архив) в выбранный период."
                    />
                    <StatsCard 
                        title="Просрочено" 
                        value={stats.overdueRequests} 
                        icon={AlertTriangle} 
                        className="text-red-600"
                        description="Дедлайн в этот период"
                        tooltipText="Количество заявок, дедлайн которых выпал на выбранный период, и они не были выполнены вовремя (ушли в просрочку)."
                    />
                    <StatsCard 
                        title="Коэф. закрытия" 
                        value={`${resolutionRate}%`} 
                        icon={TrendingUp} 
                        className={resolutionRate >= 95 ? "text-green-600" : resolutionRate > 80 ? "text-orange-500" : "text-red-600"}
                        description="Выполнено / Создано за период"
                        tooltipText="Показывает, справляется ли команда с потоком. Считается как отношение заявок, закрытых в выбранный период, к числу созданных за этот же период. Если больше 100% — команда разгребает старые долги."
                    />
                    <StatsCard 
                        title="Среднее время" 
                        value={stats.averageCompletionTimeDays ? `${stats.averageCompletionTimeDays.toFixed(1)} дн.` : "—"} 
                        icon={Clock} 
                        className="text-purple-600"
                        description="Для закрытых за период"
                        tooltipText="Скорость реакции. Среднее время от создания заявки до её окончательного закрытия админом. Учитываются только заявки, переведенные в 'Закрыта' в выбранный период."
                    />
                    <StatsCard 
                        title="SLA (Соблюдение)" 
                        value={stats.slaCompliancePercent ? `${stats.slaCompliancePercent.toFixed(0)}%` : "—"} 
                        icon={ShieldCheck} 
                        className={stats.slaCompliancePercent >= 90 ? "text-green-600" : "text-orange-500"}
                        description="Для закрытых за период"
                        tooltipText="Качество сервиса. Процент заявок, которые были закрыты без нарушения дедлайна, от общего числа закрытых в выбранный период."
                    />
                </div>

                {/* Ряд 1: Динамика и Круговая диаграмма */}
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
                    <ChartCard className="col-span-4" title="Динамика создания заявок" description="На основе даты создания за период" tooltipText="Показывает пики и спады нагрузки по дням. Помогает понять, в какие дни поступает больше всего заявок.">
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
                                    <XAxis dataKey="date" tick={{fontSize: 11}} />
                                    <YAxis allowDecimals={false} />
                                    <RechartsTooltip contentStyle={{ borderRadius: '8px' }}/>
                                    <Area type="monotone" dataKey="count" stroke="#3b82f6" fillOpacity={1} fill="url(#colorCnt)" name="Заявки" />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    </ChartCard>

                    <ChartCard className="col-span-3" title="Срочность" description="На основе даты создания за период" tooltipText="Структура входящих проблем: какая доля заявок — это аварии, а какая — плановые работы (среди созданных в выбранный период).">
                        <div className="h-[250px] flex items-center justify-center">
                            <ResponsiveContainer width="100%" height="100%">
                                <PieChart>
                                    <Pie
                                        data={urgencyData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={50}
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
                        <div className="flex justify-center gap-3 text-xs text-gray-500 flex-wrap mt-2">
                            {urgencyData.map((entry, index) => (
                                <div key={index} className="flex items-center gap-1">
                                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: COLORS[index % COLORS.length] }}></div>
                                    <span>{entry.name}: {entry.value}</span>
                                </div>
                            ))}
                        </div>
                    </ChartCard>
                </div>

                {/* Ряд 2: Антирейтинги (Просрочки) */}
                <div className="grid gap-4 md:grid-cols-2">
                        <ChartCard 
                            title={<span className="text-red-600 flex items-center gap-2"><AlertTriangle className="h-5 w-5" /> Антирейтинг подрядчиков</span>}
                            description="По дате дедлайна за период"
                            tooltipText="Показывает исполнителей, у которых больше всего заявок с дедлайном в выбранном периоде ушло в просрочку."
                            headerClassName="bg-red-50/50 pb-4 border-b"
                            className="border-red-100 shadow-sm"
                        >
                        <div className="h-[250px] pt-4">
                            {stats.worstContractors.length === 0 ? (
                                <div className="h-full flex items-center justify-center text-gray-400">Просрочек нет 🎉</div>
                            ) : (
                                <ResponsiveContainer width="100%" height="100%">
                                    <BarChart layout="vertical" data={stats.worstContractors} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                        <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                        <XAxis type="number" allowDecimals={false} hide />
                                        <YAxis dataKey="name" type="category" width={100} tick={{fontSize: 11}} />
                                        <RechartsTooltip cursor={{fill: 'transparent'}} />
                                        <Bar dataKey="value" fill="#ef4444" radius={[0, 4, 4, 0]} name="Просрочено" barSize={20} />
                                    </BarChart>
                                </ResponsiveContainer>
                            )}
                        </div>
                    </ChartCard>

                        <ChartCard 
                            title={<span className="text-red-600 flex items-center gap-2"><AlertTriangle className="h-5 w-5" /> Проблемные магазины</span>}
                            description="По дате дедлайна за период"
                            tooltipText="Показывает магазины, инфраструктура которых чаще всего страдает от затянувшихся ремонтов (по кол-ву просроченных дедлайнов в выбранный период)."
                            headerClassName="bg-red-50/50 pb-4 border-b"
                            className="border-red-100 shadow-sm"
                        >
                        <div className="h-[250px] pt-4">
                            {stats.worstShops.length === 0 ? (
                                <div className="h-full flex items-center justify-center text-gray-400">Просрочек нет 🎉</div>
                            ) : (
                                <ResponsiveContainer width="100%" height="100%">
                                    <BarChart layout="vertical" data={stats.worstShops} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                        <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                        <XAxis type="number" allowDecimals={false} hide />
                                        <YAxis dataKey="name" type="category" width={100} tick={{fontSize: 11}} />
                                        <RechartsTooltip cursor={{fill: 'transparent'}} />
                                        <Bar dataKey="value" fill="#f97316" radius={[0, 4, 4, 0]} name="Просрочено" barSize={20} />
                                    </BarChart>
                                </ResponsiveContainer>
                            )}
                        </div>
                    </ChartCard>
                </div>

                {/* Ряд 3: Загрузка подрядчиков и Топ магазинов (Общее) */}
                <div className="grid gap-4 md:grid-cols-2">
                    <ChartCard 
                        title="Текущая загрузка подрядчиков" 
                        description="Активные заявки (без фильтра по дате)"
                        tooltipText="Помогает равномерно распределять новые заявки. Учитываются все заявки со статусом 'В работе' (неважно, просрочены они или нет)."
                    >
                        <div className="h-[250px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart layout="vertical" data={stats.contractorWorkload} margin={{ top: 5, right: 30, left: 40, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} />
                                    <XAxis type="number" allowDecimals={false} />
                                    <YAxis dataKey="name" type="category" width={100} tick={{fontSize: 12}} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#f59e0b" radius={[0, 4, 4, 0]} name="В работе" barSize={15} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </ChartCard>

                    <ChartCard 
                        title="Самые активные магазины" 
                        description="По кол-ву созданных заявок за период"
                        tooltipText="Показывает 'исторически' проблемные объекты, инфраструктура которых чаще всего требует ремонта (среди созданных в период)."
                    >
                        <div className="h-[250px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={stats.topProblemShops} margin={{ top: 20, right: 30, left: 20, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="name" tick={{fontSize: 11}} interval={0} angle={-15} textAnchor="end" height={60}/>
                                    <YAxis allowDecimals={false} />
                                    <RechartsTooltip cursor={{fill: 'transparent'}} />
                                    <Bar dataKey="value" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Всего заявок" barSize={30} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </ChartCard>
                </div>

                {/* Ряд 4: Виды работ и Лидеры */}
                <div className="grid gap-4 md:grid-cols-2">
                    <ChartCard 
                        title="Топ категорий работ" 
                        description="На основе даты создания за период"
                        tooltipText="Показывает, какое оборудование ломается чаще всего (сантехника, электрика и т.д.) среди созданных в выбранный период."
                    >
                        <div className="h-[250px]">
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
                    </ChartCard>

                    <ChartCard 
                        title="Лидеры по продуктивности" 
                        description="Закрыто заявок (по дате закрытия)"
                        tooltipText="Показывает лучших работников периода. В зачёт идут только те заявки исполнителя, которые были окончательно переведены в статус 'Закрыта' (Архив) в выбранный период."
                    >
                        <div className="space-y-4">
                            {stats.topContractors.length === 0 ? (
                                <p className="text-center text-gray-400 py-6">Нет данных о выполненных заявках за этот период</p>
                            ) : stats.topContractors.map((contractor, i) => (
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
                    </ChartCard>
                </div>
            </main>
        </TooltipProvider>
    );
}

// Вспомогательный компонент для маленьких карточек с Tooltip
function StatsCard({ title, value, icon: Icon, description, className, tooltipText }) {
    return (
        <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <div className="flex items-center gap-1.5">
                    <CardTitle className="text-sm font-medium leading-tight">{title}</CardTitle>
                    {tooltipText && (
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <Info className="h-3.5 w-3.5 text-gray-400 cursor-help" />
                            </TooltipTrigger>
                            <TooltipContent side="top" className="max-w-[250px] bg-slate-800 text-white text-xs z-50">
                                <p>{tooltipText}</p>
                            </TooltipContent>
                        </Tooltip>
                    )}
                </div>
                <Icon className={cn("h-4 w-4 text-muted-foreground", className)} />
            </CardHeader>
            <CardContent>
                <div className={cn("text-2xl font-bold", className)}>{value}</div>
                <p className="text-xs text-muted-foreground mt-1">{description}</p>
            </CardContent>
        </Card>
    );
}

// Вспомогательный компонент для больших графиков с Tooltip
function ChartCard({ title, description, children, className, headerClassName, tooltipText }) {
    return (
        <Card className={className}>
            <CardHeader className={headerClassName}>
                <div className="flex items-center gap-1.5">
                    <CardTitle className="text-lg">{title}</CardTitle>
                    {tooltipText && (
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <Info className="h-4 w-4 text-gray-400 cursor-help" />
                            </TooltipTrigger>
                            <TooltipContent side="top" className="max-w-[300px] bg-slate-800 text-white text-xs z-50">
                                <p>{tooltipText}</p>
                            </TooltipContent>
                        </Tooltip>
                    )}
                </div>
                {description && <CardDescription>{description}</CardDescription>}
            </CardHeader>
            <CardContent>
                {children}
            </CardContent>
        </Card>
    );
}