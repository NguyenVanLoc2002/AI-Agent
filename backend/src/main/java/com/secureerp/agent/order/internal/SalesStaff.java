package com.secureerp.agent.order.internal;

import com.secureerp.agent.identity.Role;
import com.secureerp.agent.identity.SessionIdentity;
import com.secureerp.agent.identity.Team;
import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RBAC_MATRIX: only SALES, for their own team, handle draft orders (reconciliation, staff decision); an
 * administrator is not a business reader (BR-24). 403 otherwise.
 */
final class SalesStaff {

    private static final Logger log = LoggerFactory.getLogger(SalesStaff.class);

    private SalesStaff() {
    }

    static Team teamOf(SessionIdentity staff, String action) {
        Objects.requireNonNull(staff, "staff");
        if (staff.role() != Role.SALES || staff.team().isEmpty()) {
            log.warn("order.{} denied code=FORBIDDEN", action);
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        return staff.team().get();
    }
}
