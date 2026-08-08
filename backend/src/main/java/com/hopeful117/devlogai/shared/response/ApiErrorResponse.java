package com.hopeful117.devlogai.shared.response;

import java.time.Instant;

public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String error,
    ApiErrorCode code,
    String message,
    String path,
    String correlationId)
{
}
