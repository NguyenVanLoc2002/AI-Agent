package com.secureerp.archfixture.tools.internal;

import com.secureerp.archfixture.quote.staff.StaffQuotes;

/** ARCH-13d violation: the tool layer (model proposals) reaches the staff-only quote API. */
public class ReadsStaffQuote {

    public String read(StaffQuotes quotes, String conversation) {
        return quotes.newestOf(conversation);
    }
}
