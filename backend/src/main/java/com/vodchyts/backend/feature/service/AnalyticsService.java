package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.dto.DashboardStatsResponse;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    public Mono<DashboardStatsResponse> getDashboardStats() {
        // 1. Базовые счетчики
        Mono<Long> total = db.sql("SELECT COUNT(*) FROM Requests").map(row -> getLong(row, 0)).one();
        Mono<Long> active = db.sql("SELECT COUNT(*) FROM Requests WHERE Status = 'In work'").map(row -> getLong(row, 0)).one();
        Mono<Long> completed = db.sql("SELECT COUNT(*) FROM Requests WHERE Status IN ('Done', 'Closed')").map(row -> getLong(row, 0)).one();
        Mono<Long> overdue = db.sql("SELECT COUNT(*) FROM Requests WHERE IsOverdue = 1").map(row -> getLong(row, 0)).one();

        // 2. KPI
        Mono<Double> avgTime = db.sql(
                "SELECT COALESCE(AVG(CAST(DATEDIFF(hour, CreatedAt, ClosedAt) AS FLOAT) / 24.0), 0.0) " +
                        "FROM Requests WHERE Status IN ('Done', 'Closed') AND ClosedAt IS NOT NULL"
        ).map(row -> {
            Double val = row.get(0, Double.class);
            return val != null ? val : 0.0;
        }).one().defaultIfEmpty(0.0);

        Mono<Double> slaPercent = db.sql(
                "SELECT COALESCE(CAST(SUM(CASE WHEN IsOverdue = 0 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS FLOAT), 100.0) " +
                        "FROM Requests WHERE Status IN ('Done', 'Closed')"
        ).map(row -> {
            Double val = row.get(0, Double.class);
            return val != null ? val : 100.0;
        }).one().defaultIfEmpty(100.0);

        // 3. Стандартные графики
        Flux<DashboardStatsResponse.ChartData> byStatus = db.sql("SELECT Status, COUNT(*) as cnt FROM Requests GROUP BY Status")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("Status", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> byUrgency = db.sql("SELECT uc.UrgencyName, COUNT(r.RequestID) as cnt FROM Requests r JOIN UrgencyCategories uc ON r.UrgencyID = uc.UrgencyID GROUP BY uc.UrgencyName")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("UrgencyName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> byCategory = db.sql("SELECT TOP 5 wc.WorkCategoryName, COUNT(r.RequestID) as cnt FROM Requests r JOIN WorkCategories wc ON r.WorkCategoryID = wc.WorkCategoryID GROUP BY wc.WorkCategoryName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("WorkCategoryName", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.DateChartData> last7Days = db.sql("SELECT CAST(CreatedAt AS DATE) as CreateDate, COUNT(*) as cnt FROM Requests WHERE CreatedAt >= DATEADD(day, -7, GETDATE()) GROUP BY CAST(CreatedAt AS DATE) ORDER BY CreateDate ASC")
                .map(row -> {
                    LocalDate date = row.get("CreateDate", LocalDate.class);
                    String dateStr = date != null ? date.format(DateTimeFormatter.ofPattern("dd.MM")) : "";
                    return new DashboardStatsResponse.DateChartData(dateStr, getLong(row, "cnt"));
                }).all();

        Flux<DashboardStatsResponse.TopContractorData> topContractors = db.sql("SELECT TOP 5 u.Login, COUNT(r.RequestID) as cnt FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID WHERE r.Status IN ('Done', 'Closed') GROUP BY u.Login ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.TopContractorData(row.get("Login", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> workload = db.sql("SELECT TOP 7 u.Login, COUNT(r.RequestID) as cnt FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID WHERE r.Status = 'In work' GROUP BY u.Login ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("Login", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> problemShops = db.sql("SELECT TOP 5 s.ShopName, COUNT(r.RequestID) as cnt FROM Requests r JOIN Shops s ON r.ShopID = s.ShopID GROUP BY s.ShopName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(row.get("ShopName", String.class), getLong(row, "cnt"))).all();

        // 4. НОВОЕ: Антирейтинги (те, кто просрочил)
        Flux<DashboardStatsResponse.ChartData> worstContractors = db.sql(
                "SELECT TOP 5 u.Login, COUNT(r.RequestID) as cnt " +
                        "FROM Requests r JOIN Users u ON r.AssignedContractorID = u.UserID " +
                        "WHERE r.IsOverdue = 1 " +
                        "GROUP BY u.Login ORDER BY cnt DESC"
        ).map(row -> new DashboardStatsResponse.ChartData(row.get("Login", String.class), getLong(row, "cnt"))).all();

        Flux<DashboardStatsResponse.ChartData> worstShops = db.sql(
                "SELECT TOP 5 s.ShopName, COUNT(r.RequestID) as cnt " +
                        "FROM Requests r JOIN Shops s ON r.ShopID = s.ShopID " +
                        "WHERE r.IsOverdue = 1 " +
                        "GROUP BY s.ShopName ORDER BY cnt DESC"
        ).map(row -> new DashboardStatsResponse.ChartData(row.get("ShopName", String.class), getLong(row, "cnt"))).all();

        // Группируем Mono.zip, так как максимальное количество аргументов = 8
        return Mono.zip(
                Mono.zip(total, active, completed, overdue, avgTime, slaPercent),
                Mono.zip(byStatus.collectList(), byUrgency.collectList(), byCategory.collectList(), last7Days.collectList(), topContractors.collectList()),
                Mono.zip(workload.collectList(), problemShops.collectList(), worstContractors.collectList(), worstShops.collectList())
        ).map(tuple -> {
            var counts = tuple.getT1();
            var lists1 = tuple.getT2();
            var lists2 = tuple.getT3();
            return new DashboardStatsResponse(
                    counts.getT1(), counts.getT2(), counts.getT3(), counts.getT4(), counts.getT5(), counts.getT6(),
                    lists1.getT1(), lists1.getT2(), lists1.getT3(), lists1.getT4(), lists1.getT5(),
                    lists2.getT1(), lists2.getT2(),
                    lists2.getT3(), lists2.getT4() // Новые метрики
            );
        });
    }
}