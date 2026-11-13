package com.secureerp.archfixture.order.spi;

public interface DraftOrderPort {
    String create(String idempotencyKey);
}
