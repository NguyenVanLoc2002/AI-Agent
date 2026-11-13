package com.secureerp.archfixture.order.spi;

import java.util.List;

/** Violation ARCH-09: customer enumeration and price override on an ERP port. */
public interface CustomerSearchPort {
    List<String> searchCustomers(String text);

    void overrideUnitPrice(String sku, long price);
}
