package com.fahri.product.service;

import com.fahri.product.document.ProductSearchDocument;
import com.fahri.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductSearchService {

    void indexProduct(Product product);

    void indexAllProducts(List<Product> products);

    Map<String, Object> syncAllFromDatabase();

    /**
     * Performs multi-criteria full-text search, category filtering, and price range querying.
     *
     * @param keyword  Search term matching product name or description.
     * @param category Product category filter (optional).
     * @param minPrice Minimum price filter (optional).
     * @param maxPrice Maximum price filter (optional).
     * @param pageable Pagination and sorting specifications.
     * @return Paged Elasticsearch search documents.
     */
    Page<ProductSearchDocument> searchProducts(
            String keyword,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    );
    
    void deleteProductIndex(Long productId);
}
