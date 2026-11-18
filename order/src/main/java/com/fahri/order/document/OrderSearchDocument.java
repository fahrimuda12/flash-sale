package com.fahri.order.document;

import com.fahri.order.entity.Order;
import com.fahri.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "orders_search", createIndex = false)
public class OrderSearchDocument implements Serializable {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String orderNumber;

    @Field(type = FieldType.Long)
    private Long customerId;

    @Field(type = FieldType.Keyword)
    private String customerEmail;

    @Field(type = FieldType.Keyword)
    private String status;

    private BigDecimal totalAmount;

    private BigDecimal finalAmount;

    @Field(type = FieldType.Keyword)
    private String voucherCode;

    @Field(type = FieldType.Text)
    private String productNames;

    @Field(type = FieldType.Keyword)
    private List<String> categories;

    @Field(type = FieldType.Keyword)
    private String createdAt;

    public static OrderSearchDocument fromEntity(Order order) {
        if (order == null) {
            return null;
        }

        String productNames = "";
        List<String> categories = Collections.emptyList();

        if (order.getItems() != null && !order.getItems().isEmpty()) {
            productNames = order.getItems().stream()
                    .map(OrderItem::getProductName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining(", "));

            categories = order.getItems().stream()
                    .map(OrderItem::getCategory)
                    .filter(cat -> cat != null && !cat.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }

        return OrderSearchDocument.builder()
                .id(order.getOrderNumber())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .totalAmount(order.getTotalAmount())
                .finalAmount(order.getFinalAmount())
                .voucherCode(order.getVoucherCode())
                .productNames(productNames)
                .categories(categories)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : LocalDateTime.now().toString())
                .build();
    }
}
