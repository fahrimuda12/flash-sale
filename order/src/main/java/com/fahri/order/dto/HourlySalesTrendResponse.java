package com.fahri.order.dto;

import com.fahri.order.repository.projection.HourlySalesTrendProjection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HourlySalesTrendResponse implements Serializable {

    private String hourBucket;
    private Long orderCount;
    private BigDecimal hourlySales;
    private BigDecimal cumulativeSales;

    public static HourlySalesTrendResponse fromProjection(HourlySalesTrendProjection projection) {
        return HourlySalesTrendResponse.builder()
                .hourBucket(projection.getHourBucket())
                .orderCount(projection.getOrderCount())
                .hourlySales(projection.getHourlySales())
                .cumulativeSales(projection.getCumulativeSales())
                .build();
    }
}
