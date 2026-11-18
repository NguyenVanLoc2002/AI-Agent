package com.secureerp.agent.identity;

/** Channel the authenticated session came from. Zalo is gated (EP-12, D-05..D-07). */
public enum Channel {
    WEB_CHAT,
    ZALO
}
