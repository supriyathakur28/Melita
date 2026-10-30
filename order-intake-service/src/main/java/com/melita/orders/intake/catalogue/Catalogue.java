package com.melita.orders.intake.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Optional;

/**
 * Immutable snapshot of the catalogue: what is cached in Redis and what the validation rules read. Includes
 * inactive packages so a retired package is reported as "not available" rather than "unknown".
 */
public record Catalogue(List<ProductEntry> products) {

    public record ProductEntry(String code, String name, boolean requiresInstallation, boolean active,
                               List<PackageEntry> packages) {
    }

    public record PackageEntry(String code, String name, boolean active) {
    }

    /** A package together with the product it belongs to. */
    public record Offer(ProductEntry product, PackageEntry pkg) {
    }

    public Optional<Offer> findPackage(final String packageCode) {
        return products.stream()
                .flatMap(product -> product.packages().stream().map(pkg -> new Offer(product, pkg)))
                .filter(offer -> offer.pkg().code().equals(packageCode))
                .findFirst();
    }

    public Optional<ProductEntry> findProduct(final String productCode) {
        return products.stream().filter(p -> p.code().equals(productCode)).findFirst();
    }

    @JsonIgnore
    public List<ProductEntry> activeProducts() {
        return products.stream()
                .filter(ProductEntry::active)
                .map(p -> new ProductEntry(p.code(), p.name(), p.requiresInstallation(), true,
                        p.packages().stream().filter(PackageEntry::active).toList()))
                .toList();
    }
}
