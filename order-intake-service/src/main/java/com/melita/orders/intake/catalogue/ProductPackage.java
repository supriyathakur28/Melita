package com.melita.orders.intake.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_package")
public class ProductPackage {

    @Id
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_code")
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected ProductPackage() {
    }

    public String getCode() {
        return code;
    }

    public Product getProduct() {
        return product;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
