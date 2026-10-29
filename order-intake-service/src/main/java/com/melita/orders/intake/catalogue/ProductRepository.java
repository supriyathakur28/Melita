package com.melita.orders.intake.catalogue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    /** Loads products with their packages in one query (no N+1). */
    @EntityGraph(attributePaths = "packages")
    List<Product> findAllByOrderByCode();
}
