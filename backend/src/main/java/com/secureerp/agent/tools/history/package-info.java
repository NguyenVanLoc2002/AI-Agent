/**
 * Named interface {@code tools :: history}: read-only view of {@code tool_invocation} for the security
 * inspector (EP-09b, TOOL_CONTRACTS §1: "SECREV reads it through the inspector API"). Tool names,
 * decisions and reason codes only; never arguments, results, principals or conversations.
 */
@NamedInterface("history")
package com.secureerp.agent.tools.history;

import org.springframework.modulith.NamedInterface;
