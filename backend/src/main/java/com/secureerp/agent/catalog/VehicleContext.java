package com.secureerp.agent.catalog;

import com.secureerp.agent.platform.ConversationId;
import org.springframework.modulith.NamedInterface;

/**
 * Server-side VIN handling for a conversation (UC-02 AC-02.1, TOOL_CONTRACTS §3.1, BR-37). The full
 * VIN is validated and stored here, bound to the conversation with a TTL; the model refers to it only
 * as {@code {"vinRef":"current"}} and sees the masked form.
 *
 * <p>Called at message intake, before anything is sent to the model (EP-04; A-21, A-29). <b>Caller
 * obligations</b> (not enforced here; EP-05 reviews L-1/L1):
 * <ul>
 *   <li>the conversation comes from the authenticated session and is owned by the principal (the
 *       same check as {@code tools.spi.ConversationAccessPort}), never from request text;</li>
 *   <li>the message is the raw customer text, before the inbound sanitizer;</li>
 *   <li>on anything but {@link VinIntake.Accepted} or {@link VinIntake.None}, the model is not called
 *       for this message.</li>
 * </ul>
 */
@NamedInterface("intake")
public interface VehicleContext {

    /**
     * Reads the VINs in an inbound customer message and updates the conversation's current VIN:
     * {@link VinIntake.Accepted} stores it; {@link VinIntake.Invalid} and {@link VinIntake.Multiple}
     * clear it; {@link VinIntake.None} leaves it unchanged; {@link VinIntake.Unavailable} reports a
     * store failure (never an exception, never the VIN in a log line).
     */
    VinIntake register(ConversationId conversation, String messageText);
}
