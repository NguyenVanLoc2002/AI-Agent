package com.secureerp.agent.catalog.spi;

import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;

/**
 * The catalog backend failed. Carries only {@link ErrorCode#SERVICE_UNAVAILABLE}: the backend's own
 * message and cause are deliberately dropped, because ERP errors can contain sensitive values
 * (T-ERP-03).
 */
public class CatalogUnavailableException extends ApplicationException {

    public CatalogUnavailableException() {
        super(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
