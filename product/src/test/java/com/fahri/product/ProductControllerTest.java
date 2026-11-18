package com.fahri.product;

import com.fahri.product.controller.ProductController;
import com.fahri.product.dto.CreateProductRequest;
import com.fahri.product.dto.ProductResponse;
import com.fahri.product.exception.GlobalExceptionHandler;
import com.fahri.product.exception.NotFoundException;
import com.fahri.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Product Controller Web MVC Unit Tests")
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = ProductResponse.builder()
                .id(1L)
                .name("Flash Sale Smartphone")
                .description("Flagship device")
                .price(BigDecimal.valueOf(10000000))
                .stock(50)
                .category("Electronics")
                .build();
    }

    @Test
    @DisplayName("GET /api/products/all should return product list")
    void testGetAllProducts() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/products/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Flash Sale Smartphone"));
    }

    @Test
    @DisplayName("GET /api/products/{id} should return 200 when found")
    void testGetProductById_Success() throws Exception {
        when(productService.getProductById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Flash Sale Smartphone"));
    }

    @Test
    @DisplayName("GET /api/products/{id} should return 404 when product missing")
    void testGetProductById_NotFound() throws Exception {
        when(productService.getProductById(99L)).thenThrow(new NotFoundException("Product not found with id: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/products should create product and return 201")
    void testCreateProduct_Success() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("Flash Sale Smartphone")
                .description("Flagship device")
                .price(BigDecimal.valueOf(10000000))
                .stock(50)
                .category("Electronics")
                .build();

        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Flash Sale Smartphone"));
    }
}
