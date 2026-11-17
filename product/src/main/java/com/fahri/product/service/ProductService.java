package com.fahri.product.service;

import com.fahri.product.dto.CreateProductRequest;
import com.fahri.product.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {
    List<ProductResponse> getAllProducts();
    Page<ProductResponse> getProductsPaged(Pageable pageable);
    ProductResponse getProductById(Long id);
    ProductResponse createProduct(CreateProductRequest request);
}
