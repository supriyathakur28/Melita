package com.melita.orders.fulfilment.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fulfilment_order_item")
public class FulfilmentOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fulfilment_order_id")
    private FulfilmentOrder order;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "package_code", nullable = false)
    private String packageCode;

    @Column(name = "package_name", nullable = false)
    private String packageName;

    protected FulfilmentOrderItem() {
    }

    FulfilmentOrderItem(FulfilmentOrder order, String productCode, String packageCode, String packageName) {
        this.order = order;
        this.productCode = productCode;
        this.packageCode = packageCode;
        this.packageName = packageName;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getPackageCode() {
        return packageCode;
    }

    public String getPackageName() {
        return packageName;
    }
}
