package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.dto.DashboardStatsResponse;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
public class AnalyticsService {

    private final DatabaseClient db;

    public AnalyticsService(DatabaseClient db) {
        this.db = db;
    }

    private Long getLong(Readable row, String name) {
        Number value = row.get(name, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    private Long getLong(Readable row, int index) {
        Number value = row.get(index, Number.class);
        return value != null ? value.longValue() : 0L;
    }

    public Mono<DashboardStatsResponse> getDashboardStats(LocalDate startDate, LocalDate endDate) {
        // Устанавливаем дефолтные даты, если они не переданы
        LocalDate start = startDate != null ? startDate : LocalDate.of(2000, 1, 1);
        LocalDate end = endDate != null ? endDate : LocalDate.now().plusDays(1);

        // Для SQL BETWEEN нам нужно, чтобы end включал конец дня
        String dateFilter = " WHERE CreatedAt >= :start AND CreatedAt <= :end ";

        // 1. Базовые счетчики (за период)
        Mono<Long> total = db.sql("SELECT COUNT(*) FROM Requests" + dateFilter)
                .bind("start", start).bind("end", end).map(row -> getLong(row, 0)).one();

        Mono<Long> active = db.sql("SELECT COUNT(*) FROM Requests" + dateFilter + " AND Status = 'In work'")
                .bind("start", start).bind("end", end).map(row -> getLong(row, 0)).one();

        Mono<Long> completed = db.sql("SELECT COUNT(*) FROM Requests" + dateFilter + " AND Status IN ('Done', 'Closed')")
                .bind("start", start).bind("end", end).map(row -> getLong(row, 0)).one();

        Mono<Long> overdue = db.sql("SELECT COUNT(*) FROM Requests" + dateFilter + " AND IsOverdue = 1")
                .bind("start", start).bind("end", end).map(row -> getLong(row, 0)).one();

        // 2. KPI
        Mono<Double> avgTime = db.sql(
                "SELECT COALESCE(AVG(CAST(DATEDIFF(hour, CreatedAt, ClosedAt) AS FLOAT) / 24.0), 0.0) " +
                        "FROM Requests " + dateFilter + " AND Status IN ('Done', 'Closed') AND ClosedAt IS NOT NULL"
        ).bind("start", start).bind("end", end).map(row -> {
            Double val = row.get(0, Double.class);
            return val != null ? val : 0.0;
        }).one().defaultIfEmpty(0.0);

        Mono<Double> slaPercent = db.sql(
                "SELECT COALESCE(CAST(SUM(CASE WHEN IsOverdue = 0 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS FLOAT), 100.0) " +
                        "FROM Requests " + dateFilter + " AND Status IN ('Done', 'Closed')"
        ).bind("start", start).bind("end", end).map(row -> {
            Double val = row.get(0, Double.class);
            return val != null ? val : 100.0;
        }).one().defaultIfEmpty(100.0);

        // 3. Стандартные графики (с фильтром по дате)
        Flux<DashboardStatsResponse.ChartData> byStatus = db.sql("SELECT Status, COUNT(*) as cnt FROM Requests " + dateFilter + " GROUP BY Status")
                .bind("start", start).bind("end", end).map(row -> new DashboardStatsResponse.ChartData(row.get("Status", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> byUrgency = db.sql("SELECT uc.UrgencyName, COUNT(r.RequestID) as cnt FROM Requests r JOIN UrgencyCategories uc ON r.UrgencyID = uc.UrgencyID " + dateFilter.replace("WHERE", "WHERE r.") + " GROUP BY uc.UrgencyName")
                .bind("start", start).bind("end", end).map(row -> new DashboardStatsResponse.ChartData(row.get("UrgencyName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> byCategory = db.sql("SELECT TOP 5 wc.WorkCategoryName, COUNT(r.RequestID) as cnt FROM Requests r JOIN WorkCategories wc ON r.WorkCategoryID = wc.WorkCategoryID " + dateFilter.replace("WHERE", "WHERE r.") + " GROUP BY wc.WorkCategoryName ORDER BY cnt DESC")
                .bind("start", start).bind("end", end).map(row -> new DashboardStatsResponse.ChartData(row.get("WorkCategoryName", String.class), getLong(row, "cnt"))).all();

        // ДИНАМИЧЕСКИЙ ГРАФИК (по дням или месяцам в зависимости от размаха дат)
        long daysBetween = ChronoUnit.DAYS.between(start, end);
        String dynamicsSql;
        if (daysBetween > 60) {
            // Группировка по месяцам (Год-Месяц)
            dynamicsSql = "SELECT FORMAT(CreatedAt, 'yyyy-MM') as CreateDate, COUNT(*) as cnt FROM Requests " + dateFilter + " GROUP BY FORMAT(CreatedAt, 'yyyy-MM') ORDER BY CreateDate ASC";
        } else {
            // Группировка по дням
            dynamicsSql = "SELECT CAST(CreatedAt AS DATE) as CreateDate, COUNT(*) as cnt FROM Requests " + dateFilter + " GROUP BY CAST(CreatedAt AS DATE) ORDER BY CreateDate ASC";
        }

        Flux<DashboardStatsResponse.DateChartData> dynamics = db.sql(dynamicsSql)
                .bind("start", start).bind("end", end)
                .map(row -> {
                    if (daysBetween > 60) {
                        return new DashboardStatsResponse.DateChartData(row.get("CreateDate", String.class), getLong(row, "cnt"));
                    } else {
                        LocalDate date = row.get("CreateDate", LocalDate.class);
                        String dateStr = date != null ? date.format(DateTimeFormatter.ofPattern("dd.MM")) : "";
                        return new DashboardStatsResponse.DateChartData(dateStr, getLong(row, "cnt"));
                    }
                }).all();

        Flux<DashboardStatsResponse.TopContractorData> topContractors = db.sql("SELECT TOP 5 u.FullName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID " + dateFilter.replace("WHERE", "WHERE r.") + " AND r.Status IN ('Done', 'Closed') GROUP BY u.FullName ORDER BY cnt DESC")
                .bind("start", start).bind("end", end)
                .map(row -> new DashboardStatsResponse.TopContractorData(row.get("FullName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> workload = db.sql("SELECT TOP 7 u.FullName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID WHERE r.Status = 'In work' GROUP BY u.FullName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("FullName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> problemShops = db.sql("SELECT TOP 5 s.ShopName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Shops s ON r.ShopID = s.ShopID " + dateFilter.replace("WHERE", "WHERE r.") + " GROUP BY s.ShopName ORDER BY cnt DESC")
                .bind("start", start).bind("end", end).map(row -> new DashboardStatsResponse.ChartData(row.get("ShopName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> worstContractors = db.sql("SELECT TOP 5 u.FullName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID WHERE r.IsOverdue = 1 GROUP BY u.FullName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("FullName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> worstShops = db.sql("SELECT TOP 5 s.ShopName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Shops s ON r.ShopID = s.ShopID WHERE r.IsOverdue = 1 GROUP BY s.ShopName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("ShopName", String.class), getLong(row, "cnt"))).all();

        return Mono.zip(
                Mono.zip(total, active, completed, overdue, avgTime, slaPercent),
                Mono.zip(byStatus.collectList(), byUrgency.collectList(), byCategory.collectList(), dynamics.collectList(), topContractors.collectList()),
                Mono.zip(workload.collectList(), problemShops.collectList(), worstContractors.collectList(), worstShops.collectList())
        ).map(tuple -> {
            var counts = tuple.getT1();
            var lists1 = tuple.getT2();
            var lists2 = tuple.getT3();
            return new DashboardStatsResponse(
                    counts.getT1(), counts.getT2(), counts.getT3(), counts.getT4(), counts.getT5(), counts.getT6(),
                    lists1.getT1(), lists1.getT2(), lists1.getT3(), lists1.getT4(), lists1.getT5(),
                    lists2.getT1(), lists2.getT2(), lists2.getT3(), lists2.getT4()
            );
        });
    }
}