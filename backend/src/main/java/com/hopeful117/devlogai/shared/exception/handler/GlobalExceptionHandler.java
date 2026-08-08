package com.hopeful117.devlogai.shared.exception.handler;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskConflictResponse;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.analysis.workflow.exception.UnsupportedAnalysisTypeException;
import com.hopeful117.devlogai.project.exception.ProjectSlugAlreadyExistsException;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.logging.CorrelationIdFilter;
import com.hopeful117.devlogai.shared.response.ApiErrorCode;
import com.hopeful117.devlogai.shared.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AiTaskResultConflictException.class)
    public ResponseEntity<AiTaskConflictResponse> handleAiTaskResultConflict(
            AiTaskResultConflictException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new AiTaskConflictResponse(
                        Instant.now(),
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        ex.getCode(),
                        ex.getCurrentStatus(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        correlationId(request)
                )
        );
    }

    @ExceptionHandler(UnsupportedAnalysisTypeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedAnalysisType(
            UnsupportedAnalysisTypeException ex,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.UNSUPPORTED_ANALYSIS_TYPE, ex.getMessage(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleRouteNotFound(
            Exception ex,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, ApiErrorCode.ROUTE_NOT_FOUND,
                "No API route matches this request.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = body(HttpStatus.METHOD_NOT_ALLOWED,
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method '%s' is not supported for this route."
                        .formatted(ex.getMethod()), request);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String message = "Invalid value for parameter '" + ex.getName() + "'.";
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_PARAMETER,
                message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST,
                "Malformed or invalid JSON request.", request);
    }

    @ExceptionHandler(InvalidAiTaskResultException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAiTaskResult(
            InvalidAiTaskResultException ex,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_AI_TASK_RESULT,
                ex.getMessage(), request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        return response(HttpStatus.NOT_FOUND, ApiErrorCode.ENTITY_NOT_FOUND,
                ex.getMessage(), request);
    }

    @ExceptionHandler(ProjectSlugAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectSlugAlreadyExists(
            ProjectSlugAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT,
                ApiErrorCode.PROJECT_SLUG_ALREADY_EXISTS, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException ex,
            HttpServletRequest request) {

        return response(HttpStatus.CONFLICT, ApiErrorCode.RESOURCE_CONFLICT,
                ex.getMessage(), request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation failed");

        return response(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
                message, request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Unhandled exception method={} path={} exceptionType={}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getClass().getName(),
                ex
        );

        return response(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(body(status, code, message, request));
    }

    private ApiErrorResponse body(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                correlationId(request)
        );
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String correlationId ? correlationId : null;
    }
}
