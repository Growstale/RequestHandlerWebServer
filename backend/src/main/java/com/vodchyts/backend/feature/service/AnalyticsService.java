package com.vodchyts.backend.feature.service;

import com.vodchyts.backend.feature.dto.DashboardStatsResponse;
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

    public Mono<DashboardStatsResponse> getDashboardStats() {
        // 1. Счетчики (KPI)
        Mono<Long> total = db.sql("SELECT COUNT(*) FROM Requests").map(row -> row.get(0, Long.class)).one();
        Mono<Long> active = db.sql("SELECT COUNT(*) FROM Requests WHERE Status = 'In work'").map(row -> row.get(0, Long.class)).one();
        Mono<Long> completed = db.sql("SELECT COUNT(*) FROM Requests WHERE Status IN ('Done', 'Closed')").map(row -> row.get(0, Long.class)).one();
        Mono<Long> overdue = db.sql("SELECT COUNT(*) FROM Requests WHERE IsOverdue = 1").map(row -> row.get(0, Long.class)).one();

        // 2. График по Статусам
        Flux<DashboardStatsResponse.ChartData> byStatus = db.sql(
                        "SELECT Status, COUNT(*) as cnt FROM Requests GROUP BY Status")
                .map(row -> new DashboardStatsResponse.ChartData(
                        row.get("Status", String.class),
                        row.get("cnt", Long.class)
                )).all();

        // 3. График по Срочности
        Flux<DashboardStatsResponse.ChartData> byUrgency = db.sql(
                        "SELECT uc.UrgencyName, COUNT(r.RequestID) as cnt " +
                                "FROM Requests r JOIN UrgencyCategories uc ON r.UrgencyID = uc.UrgencyID " +
                                "GROUP BY uc.UrgencyName")
                .map(row -> new DashboardStatsResponse.ChartData(
                        row.get("UrgencyName", String.class),
                        row.get("cnt", Long.class)
                )).all();

        // 4. График по Категориям работ (Топ 5)
        Flux<DashboardStatsResponse.ChartData> byCategory = db.sql(
                        "SELECT TOP 5 wc.WorkCategoryName, COUNT(r.RequestID) as cnt " +
                                "FROM Requests r JOIN WorkCategories wc ON r.WorkCategoryID = wc.WorkCategoryID " +
                                "GROUP BY wc.WorkCategoryName ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.ChartData(
                        row.get("WorkCategoryName", String.class),
                        row.get("cnt", Long.class)
                )).all();

        // 5. Тренд за последние 7 дней (MSSQL Syntax)
        Flux<DashboardStatsResponse.DateChartData> last7Days = db.sql(
                        "SELECT CAST(CreatedAt AS DATE) as CreateDate, COUNT(*) as cnt " +
                                "FROM Requests " +
                                "WHERE CreatedAt >= DATEADD(day, -7, GETDATE()) " +
                                "GROUP BY CAST(CreatedAt AS DATE) " +
                                "ORDER BY CreateDate ASC")
                .map(row -> {
                    LocalDate date = row.get("CreateDate", LocalDate.class);
                    return new DashboardStatsResponse.DateChartData(
                            date.format(DateTimeFormatter.ofPattern("dd.MM")),
                            row.get("cnt", Long.class)
                    );
                }).all();

        // 6. Топ исполнителей (по выполненным заявкам)
        Flux<DashboardStatsResponse.TopContractorData> topContractors = db.sql(
                        "SELECT TOP 5 u.Login, COUNT(r.RequestID) as cnt " +
                                "FROM Requests r " +
                                "JOIN Users u ON r.AssignedContractorID = u.UserID " +
                                "WHERE r.Status IN ('Done', 'Closed') " +
                                "GROUP BY u.Login ORDER BY cnt DESC")
                .map(row -> new DashboardStatsResponse.TopContractorData(
                        row.get("Login", String.class),
                        row.get("cnt", Long.class)
                )).all();

        return Mono.zip(total, active, completed, overdue)
                .flatMap(counts ->
                        Mono.zip(
                                byStatus.collectList(),
                                byUrgency.collectList(),
                                byCategory.collectList(),
                                last7Days.collectList(),
                                topContractors.collectList()
                        ).map(lists -> new DashboardStatsResponse(
                                counts.getT1(),
                                counts.getT2(),
                                counts.getT3(),
                                counts.getT4(),
                                lists.getT1(),
                                lists.getT2(),
                                lists.getT3(),
                                lists.getT4(),
                                lists.getT5()
                        ))
                );
    }
}