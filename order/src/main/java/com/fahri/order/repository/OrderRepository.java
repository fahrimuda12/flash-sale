package com.fahri.order.repository;

import com.fahri.order.entity.Order;
import com.fahri.order.repository.projection.CustomerSpendingProjection;
import com.fahri.order.repository.projection.HourlySalesTrendProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    @Query(value = "SELECT " +
            "    o.customer_id AS customerId, " +
            "    o.customer_email AS customerEmail, " +
            "    SUM(o.final_amount) AS totalSpent, " +
            "    COUNT(o.id) AS orderCount, " +
            "    DENSE_RANK() OVER (ORDER BY SUM(o.final_amount) DESC) AS customerRank " +
            "FROM orders o " +
            "WHERE o.status IN ('CONFIRMED', 'COMPLETED') " +
            "GROUP BY o.customer_id, o.customer_email " +
            "ORDER BY customerRank ASC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<CustomerSpendingProjection> findTopSpendersNative(@Param("limit") int limit);

    @Query(value = "WITH hourly_buckets AS ( " +
            "    SELECT " +
            "        TO_CHAR(o.created_at, 'YYYY-MM-DD HH24:00') AS hourBucket, " +
            "        COUNT(o.id) AS orderCount, " +
            "        SUM(o.final_amount) AS hourlySales " +
            "    FROM orders o " +
            "    WHERE o.status IN ('CONFIRMED', 'COMPLETED') " +
            "    GROUP BY TO_CHAR(o.created_at, 'YYYY-MM-DD HH24:00') " +
            ") " +
            "SELECT " +
            "    hb.hourBucket AS hourBucket, " +
            "    hb.orderCount AS orderCount, " +
            "    hb.hourlySales AS hourlySales, " +
            "    SUM(hb.hourlySales) OVER ( " +
            "        ORDER BY hb.hourBucket " +
            "        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW " +
            "    ) AS cumulativeSales " +
            "FROM hourly_buckets hb " +
            "ORDER BY hb.hourBucket ASC",
            nativeQuery = true)
    List<HourlySalesTrendProjection> findHourlySalesTrendsNative();
}
