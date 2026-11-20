/**
 * Staff-only quote APIs (EP-08b, A-43): the read-only quote view of a conversation a staff member holds
 * ({@link com.secureerp.agent.quote.staff.StaffQuotes}) and the A-34 lock lift at resolve
 * ({@link com.secureerp.agent.quote.staff.QuoteLocks}). Both trust the caller to have checked the handoff,
 * because quote may not depend on handoff. Exposed as the Modulith named interface {@code quote :: staff}
 * and allowed for {@code conversation} only, so {@code tools} (which acts on model proposals) cannot reach
 * them (architecture review EP-08b M1).
 */
@NamedInterface("staff")
package com.secureerp.agent.quote.staff;

import org.springframework.modulith.NamedInterface;
