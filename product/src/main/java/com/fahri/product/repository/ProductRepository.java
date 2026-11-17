package com.fahri.product.repository;

import com.fahri.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = "SELECT * " +
            "FROM products p " +
            "WHERE p.stock > 0 " +
            "ORDER BY p.created_at DESC",
           countQuery = "SELECT COUNT(*) " +
                   "FROM products p " +
                   "WHERE p.stock > 0",
           nativeQuery = true)
    Page<Product> findAvailableProducts(Pageable pageable);
}
