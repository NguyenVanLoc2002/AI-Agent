---
name: agent-tool-contract
description: Define least-privilege safe LLM tools
---
# Workflow
Define tool name, actor/channel and ownership, approved schema with additionalProperties:false, bounds, authorization decisions, quotas and output fields. Customer identity is derived from server principal; never add tenantId/customerId arguments. Return safe technical data only and opaque quote handle; deny unknown/extra/nested parameters, broad enumeration, output prompt injection. Include safe error/fallback, audit event and tests: authorized, cross-customer, oversized, malicious and outage. Produce contract and test fixtures before implementation.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
