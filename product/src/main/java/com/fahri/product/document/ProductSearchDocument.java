package com.fahri.product.document;

import com.fahri.product.entity.Product;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(indexName = "products_search", createIndex = false)
public class ProductSearchDocument implements Serializable {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String createdAt;

    @Field(type = FieldType.Keyword)
    private String updatedAt;

    public static ProductSearchDocument fromEntity(Product product) {
        if (product == null) {
            return null;
        }
        return ProductSearchDocument.builder()
                .id(product.getId() != null ? String.valueOf(product.getId()) : null)
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .category(product.getCategory())
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt().toString() : LocalDateTime.now().toString())
                .updatedAt(product.getUpdatedAt() != null ? product.getUpdatedAt().toString() : LocalDateTime.now().toString())
                .build();
    }
}
