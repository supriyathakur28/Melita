package com.melita.orders.intake.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Reference data, read-only in this service: rows are created by Flyway migrations. */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "requires_installation", nullable = false)
    private boolean requiresInstallation;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "product")
    @OrderBy("code")
    private List<ProductPackage> packages = new ArrayList<>();

    protected Product() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isRequiresInstallation() {
        return requiresInstallation;
    }

    public boolean isActive() {
        return active;
    }

    public List<ProductPackage> getPackages() {
        return packages;
    }
}
