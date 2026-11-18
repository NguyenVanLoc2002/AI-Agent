package com.secureerp.agent.catalog.spi;

/** Coarse availability (BRD §08). The catalog never sees stock quantities (A-12, D-12). */
public enum AvailabilityBucket {
    CON_HANG,
    HET_HANG,
    CAN_KIEM_TRA
}
