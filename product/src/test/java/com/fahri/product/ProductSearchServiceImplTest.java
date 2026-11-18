package com.fahri.product;

import com.fahri.product.document.ProductSearchDocument;
import com.fahri.product.entity.Product;
import com.fahri.product.repository.ProductRepository;
import com.fahri.product.repository.ProductSearchRepository;
import com.fahri.product.service.impl.ProductSearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Search Service Implementation Unit Tests")
class ProductSearchServiceImplTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private ProductSearchServiceImpl productSearchService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id(1L)
                .name("Samsung Galaxy S24 Ultra")
                .description("Titanium flagship with AI features")
                .price(BigDecimal.valueOf(19999000))
                .stock(30)
                .category("Smartphone")
                .build();
    }

    @Test
    @DisplayName("indexProduct should convert entity and save to Elasticsearch")
    void testIndexProduct_Success() {
        productSearchService.indexProduct(sampleProduct);

        verify(productSearchRepository, times(1)).save(argThat(doc ->
                doc.getId().equals("1") &&
                doc.getName().equals("Samsung Galaxy S24 Ultra") &&
                doc.getCategory().equals("Smartphone")
        ));
    }

    @Test
    @DisplayName("indexAllProducts should batch save documents via Java Stream")
    void testIndexAllProducts_Success() {
        Product product2 = Product.builder()
                .id(2L)
                .name("MacBook Pro 14 M3")
                .description("Apple Silicon laptop")
                .price(BigDecimal.valueOf(31999000))
                .stock(20)
                .category("Laptop")
                .build();

        productSearchService.indexAllProducts(List.of(sampleProduct, product2));

        verify(productSearchRepository, times(1)).saveAll(argThat(docs -> {
            List<?> list = (List<?>) docs;
            return list.size() == 2;
        }));
    }

    @Test
    @DisplayName("syncAllFromDatabase should retrieve all products from DB and persist to ES")
    void testSyncAllFromDatabase_Success() {
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct));

        Map<String, Object> result = productSearchService.syncAllFromDatabase();

        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("totalSynced"));
        assertTrue((Long) result.get("executionTimeMs") >= 0);

        verify(productRepository, times(1)).findAll();
        verify(productSearchRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("searchProducts should query ElasticsearchOperations and return paged results")
    void testSearchProducts_Success() {
        ProductSearchDocument doc = ProductSearchDocument.fromEntity(sampleProduct);
        SearchHit<ProductSearchDocument> hit = new SearchHit<>(
                "products_search", "1", null, 1.0f, null, null, null, null, null, null, doc);
        SearchHits<ProductSearchDocument> hits = new SearchHitsImpl<>(
                1, TotalHitsRelation.EQUAL_TO, 1.0f, null, null, null, List.of(hit), null, null, null);

        when(elasticsearchOperations.search(any(Query.class), eq(ProductSearchDocument.class)))
                .thenReturn(hits);

        Page<ProductSearchDocument> page = productSearchService.searchProducts(
                "Galaxy", "Smartphone", BigDecimal.valueOf(10000000), BigDecimal.valueOf(25000000), PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("Samsung Galaxy S24 Ultra", page.getContent().get(0).getName());
        verify(elasticsearchOperations, times(1)).search(any(Query.class), eq(ProductSearchDocument.class));
    }

    @Test
    @DisplayName("searchProducts should gracefully fallback to database if Elasticsearch is unavailable")
    void testSearchProducts_FallbackToDatabaseOnError() {
        when(elasticsearchOperations.search(any(Query.class), eq(ProductSearchDocument.class)))
                .thenThrow(new RuntimeException("Connection refused to Elasticsearch:9200"));

        when(productRepository.findAvailableProducts(any())).thenReturn(new PageImpl<>(List.of(sampleProduct)));

        Page<ProductSearchDocument> page = productSearchService.searchProducts(
                "Galaxy", "Smartphone", null, null, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("Samsung Galaxy S24 Ultra", page.getContent().get(0).getName());
        verify(productRepository, times(1)).findAvailableProducts(any());
    }

    @Test
    @DisplayName("deleteProductIndex should invoke repository deleteById")
    void testDeleteProductIndex_Success() {
        productSearchService.deleteProductIndex(1L);

        verify(productSearchRepository, times(1)).deleteById("1");
    }
}
