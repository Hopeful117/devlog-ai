# Bugfix — LLM_PROVIDER_ERROR: Request Timed Out

## Status

Draft

## Priority

Critical

## Objective

Fix the recurring `LLM_PROVIDER_ERROR: Request timed out` failures that make
understanding refreshes and other LLM-dependent operations unreliable.

## Motivation

The `LLM_PROVIDER_ERROR` with timeout is sufficiently recurrent to be
considered critical. After Story 0045's payload compaction fix, timeouts
still occur, indicating the 30-second default timeout is insufficient for
the current OpenAI API response times.

## Root Cause Analysis

1. **Timeout mismatch**: Backend allows 45s (`AI_ENGINE_READ_TIMEOUT`), but
   AI engine only allows 30s (`LLM_TIMEOUT_SECONDS`) for the LLM call.
2. **No retry logic**: Transient timeout errors are not retried.
3. **Generic error handling**: All provider errors are caught as
   `LLM_PROVIDER_ERROR` without distinguishing transient vs permanent failures.

## Scope

### In Scope

1. Increase `LLM_TIMEOUT_SECONDS` default from 30 to 90 seconds
2. Add retry logic for transient timeout errors (max 2 retries)
3. Log detailed timeout information for debugging
4. Add `LLM_MAX_RETRIES` configuration parameter

### Out Of Scope

* Changing the LLM provider
* Modifying prompt structure
* Backend timeout changes

## Acceptance Criteria

* AC-1: Default LLM timeout increased to 90 seconds
* AC-2: Transient timeout errors are retried up to 2 times
* AC-3: Detailed timeout information is logged
* AC-4: All existing tests pass
* AC-5: Configuration is documented

## Dependencies

* Story 0045 — Fix Understanding Refresh LLM Timeout
