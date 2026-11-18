package com.fahri.product.service.impl;

import com.fahri.product.document.ProductSearchDocument;
import com.fahri.product.entity.Product;
import com.fahri.product.repository.ProductRepository;
import com.fahri.product.repository.ProductSearchRepository;
import com.fahri.product.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchServiceImpl implements ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void indexProduct(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }

        try {
            ProductSearchDocument doc = ProductSearchDocument.fromEntity(product);
            productSearchRepository.save(doc);
            log.debug("Successfully indexed product id {} into Elasticsearch", product.getId());
        } catch (Exception ex) {
            log.warn("Non-critical: Elasticsearch indexing skipped for product id {}: {}", product.getId(), ex.getMessage());
        }
    }

    @Override
    public void indexAllProducts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        try {
            List<ProductSearchDocument> docs = products.stream()
                    .filter(p -> p != null && p.getId() != null)
                    .map(ProductSearchDocument::fromEntity)
                    .collect(Collectors.toList());

            productSearchRepository.saveAll(docs);
            log.info("Batch indexed {} products into Elasticsearch index 'products_search'", docs.size());
        } catch (Exception ex) {
            log.warn("Non-critical: Elasticsearch batch indexing skipped: {}", ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> syncAllFromDatabase() {
        long startTime = System.currentTimeMillis();
        log.info("Starting full database synchronization from PostgreSQL to Elasticsearch...");

        List<Product> allProducts = productRepository.findAll();
        List<ProductSearchDocument> docs = allProducts.stream()
                .map(ProductSearchDocument::fromEntity)
                .collect(Collectors.toList());

        productSearchRepository.saveAll(docs);
        long executionTimeMs = System.currentTimeMillis() - startTime;

        log.info("Successfully synchronized {} products into Elasticsearch in {} ms", docs.size(), executionTimeMs);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "SUCCESS");
        summary.put("message", "Successfully synchronized all products from database to Elasticsearch");
        summary.put("totalSynced", docs.size());
        summary.put("executionTimeMs", executionTimeMs);

        return summary;
    }

    @Override
    public Page<ProductSearchDocument> searchProducts(
            String keyword,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        log.debug("Executing Elasticsearch search - keyword: '{}', category: '{}', priceRange: [{}, {}]",
                keyword, category, minPrice, maxPrice);

        try {
            Criteria criteria = new Criteria();
            boolean hasCriteria = false;

            if (keyword != null && !keyword.isBlank()) {
                String cleanKeyword = keyword.trim();
                Criteria keywordCriteria = new Criteria("name").contains(cleanKeyword)
                        .or(new Criteria("description").contains(cleanKeyword));
                criteria = criteria.subCriteria(keywordCriteria);
                hasCriteria = true;
            }

            if (category != null && !category.isBlank()) {
                Criteria categoryCriteria = new Criteria("category").is(category.trim());
                criteria = hasCriteria ? criteria.and(categoryCriteria) : categoryCriteria;
                hasCriteria = true;
            }

            if (minPrice != null && maxPrice != null) {
                Criteria priceCriteria = new Criteria("price").between(minPrice, maxPrice);
                criteria = hasCriteria ? criteria.and(priceCriteria) : priceCriteria;
                hasCriteria = true;
            } else if (minPrice != null) {
                Criteria priceCriteria = new Criteria("price").greaterThanEqual(minPrice);
                criteria = hasCriteria ? criteria.and(priceCriteria) : priceCriteria;
                hasCriteria = true;
            } else if (maxPrice != null) {
                Criteria priceCriteria = new Criteria("price").lessThanEqual(maxPrice);
                criteria = hasCriteria ? criteria.and(priceCriteria) : priceCriteria;
                hasCriteria = true;
            }

            Query query = hasCriteria ? new CriteriaQuery(criteria, pageable) : new CriteriaQuery(new Criteria(), pageable);
            SearchHits<ProductSearchDocument> searchHits = elasticsearchOperations.search(query, ProductSearchDocument.class);

            List<ProductSearchDocument> content = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, searchHits.getTotalHits());

        } catch (Exception ex) {
            log.warn("Elasticsearch search query failed ({}), gracefully falling back to database search", ex.getMessage());

            Page<Product> dbProducts = productRepository.findAvailableProducts(pageable);
            List<ProductSearchDocument> fallbackDocs = dbProducts.getContent().stream()
                    .filter(p -> {
                        boolean match = true;
                        if (keyword != null && !keyword.isBlank()) {
                            String term = keyword.toLowerCase();
                            match = (p.getName() != null && p.getName().toLowerCase().contains(term))
                                    || (p.getDescription() != null && p.getDescription().toLowerCase().contains(term));
                        }
                        if (match && category != null && !category.isBlank()) {
                            match = p.getCategory() != null && p.getCategory().equalsIgnoreCase(category.trim());
                        }
                        if (match && minPrice != null) {
                            match = p.getPrice() != null && p.getPrice().compareTo(minPrice) >= 0;
                        }
                        if (match && maxPrice != null) {
                            match = p.getPrice() != null && p.getPrice().compareTo(maxPrice) <= 0;
                        }
                        return match;
                    })
                    .map(ProductSearchDocument::fromEntity)
                    .collect(Collectors.toList());

            return new PageImpl<>(fallbackDocs, pageable, dbProducts.getTotalElements());
        }
    }

    @Override
    public void deleteProductIndex(Long productId) {
        if (productId == null) {
            return;
        }

        try {
            productSearchRepository.deleteById(String.valueOf(productId));
            log.debug("Deleted product id {} from Elasticsearch index", productId);
        } catch (Exception ex) {
            log.warn("Non-critical: Elasticsearch deletion skipped for product id {}: {}", productId, ex.getMessage());
        }
    }
}
