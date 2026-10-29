package com.melita.orders.intake.catalogue;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogue")
class CatalogueController {

    private final CatalogueService catalogueService;

    CatalogueController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    record ProductResponse(String code, String name, boolean requiresInstallation, List<PackageResponse> packages) {
    }

    record PackageResponse(String code, String name) {
    }

    @Operation(summary = "Products and packages that can currently be ordered")
    @GetMapping("/products")
    List<ProductResponse> products() {
        return catalogueService.current().activeProducts().stream()
                .map(p -> new ProductResponse(p.code(), p.name(), p.requiresInstallation(),
                        p.packages().stream().map(pkg -> new PackageResponse(pkg.code(), pkg.name())).toList()))
                .toList();
    }
}
