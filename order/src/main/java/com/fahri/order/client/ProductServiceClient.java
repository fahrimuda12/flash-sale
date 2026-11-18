package com.fahri.order.client;

import com.fahri.order.client.dto.ProductDto;
import com.fahri.order.exception.FlashSaleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(@Value("${app.product-service.url:http://localhost:8081}") String productServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(productServiceUrl)
                .build();
    }

    public ProductDto getProductById(Long productId) {
        log.info("Fetching product details for productId: {} from product-service", productId);
        try {
            ProductDto product = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductDto.class);

            if (product == null) {
                throw new FlashSaleException("Product not found with id: " + productId);
            }

            return product;
        } catch (FlashSaleException fe) {
            throw fe;
        } catch (Exception e) {
            log.error("Failed to fetch product details from product-service for id {}: {}", productId, e.getMessage());
            throw new FlashSaleException("Failed to retrieve product details for product id: " + productId);
        }
    }
}
