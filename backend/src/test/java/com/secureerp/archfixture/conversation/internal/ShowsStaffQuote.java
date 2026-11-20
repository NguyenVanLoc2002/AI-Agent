package com.secureerp.archfixture.conversation.internal;

import com.secureerp.archfixture.quote.staff.StaffQuotes;

/** ARCH-13d compliant: conversation reads the staff quote after checking the handoff holder. */
public class ShowsStaffQuote {

    public String read(StaffQuotes quotes, String conversation) {
        return quotes.newestOf(conversation);
    }
}
