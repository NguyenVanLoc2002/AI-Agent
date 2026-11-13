---
name: privacy-egress-review
description: Review all possible outbound data
---
# Workflow
Enumerate egress sinks: model provider, VIN/OE service, Zalo, APM, errors, retry requests, traces. Trace actual serialized bytes from user message, history, tool result, attachments and exception branches. Confirm typed allowlists and no cost, per-customer price, debt, customer identity, credentials, numeric stock or unapproved images. Use synthetic sentinel values and capture at provider adapter. Output source-to-sink matrix, violations, recommended fix and evidence or NOT VERIFIED. Never paste raw live payload.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
