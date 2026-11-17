package com.fahri.product.dto;

import com.fahri.product.entity.Product;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse implements Serializable {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String formattedPrice;
    private Integer stock;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .formattedPrice(formattingPrice(product.getPrice()))
                .stock(product.getStock())
                .category(product.getCategory())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private static String formattingPrice(BigDecimal amount) {
        if (amount == null) return "Rp 0";
        NumberFormat rupiahFormat = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        rupiahFormat.setMaximumFractionDigits(0);
        return rupiahFormat.format(amount);
    }
}
