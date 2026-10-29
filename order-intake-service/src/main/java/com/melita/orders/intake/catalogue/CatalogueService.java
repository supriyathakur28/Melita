package com.melita.orders.intake.catalogue;

import com.melita.orders.intake.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogueService {

    private final ProductRepository products;

    public CatalogueService(ProductRepository products) {
        this.products = products;
    }

    /** Cache-aside: one entry for the whole catalogue (it is small and looked up by code). */
    @Cacheable(cacheNames = CacheConfig.CATALOGUE, key = "'all'", sync = true)
    @Transactional(readOnly = true)
    public Catalogue current() {
        return new Catalogue(products.findAllByOrderByCode().stream()
                .map(p -> new Catalogue.ProductEntry(p.getCode(), p.getName(), p.isRequiresInstallation(),
                        p.isActive(), p.getPackages().stream()
                        .map(pkg -> new Catalogue.PackageEntry(pkg.getCode(), pkg.getName(), pkg.isActive()))
                        .toList()))
                .toList());
    }
}
