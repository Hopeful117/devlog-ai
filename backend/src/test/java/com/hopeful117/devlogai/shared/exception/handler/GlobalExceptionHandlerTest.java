package com.hopeful117.devlogai.shared.exception.handler;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskConflictResponse;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.workflow.exception.UnsupportedAnalysisTypeException;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.shared.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest mockRequest(String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    @Test
    void shouldHandleAiTaskResultConflict() {
        AiTaskResultConflictException ex = new AiTaskResultConflictException(
                "STATUS_CONFLICT", AiTaskStatus.COMPLETED, "Task already completed");

        ResponseEntity<AiTaskConflictResponse> response = handler.handleAiTaskResultConflict(
                ex, mockRequest("/api/v1/ai-tasks/result"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        AiTaskConflictResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("STATUS_CONFLICT", body.code());
        assertEquals(AiTaskStatus.COMPLETED, body.currentStatus());
        assertEquals("Task already completed", body.message());
        assertEquals("/api/v1/ai-tasks/result", body.path());
    }

    @Test
    void shouldHandleUnsupportedAnalysisType() {
        UnsupportedAnalysisTypeException ex = new UnsupportedAnalysisTypeException(
                AnalysisType.ARCHITECTURE_REVIEW);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnsupportedAnalysisType(
                ex, mockRequest("/api/v1/analyses"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.message().contains("ARCHITECTURE_REVIEW"));
        assertEquals("/api/v1/analyses", body.path());
    }

    @Test
    void shouldHandleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("analysisId");

        ResponseEntity<ApiErrorResponse> response = handler.handleTypeMismatch(
                ex, mockRequest("/api/v1/analyses/invalid"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.message().contains("analysisId"));
        assertEquals("/api/v1/analyses/invalid", body.path());
    }

    @Test
    void shouldHandleUnreadableMessage() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableMessage(
                ex, mockRequest("/api/v1/proposals"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Malformed or invalid JSON request.", body.message());
        assertEquals("/api/v1/proposals", body.path());
    }

    @Test
    void shouldHandleInvalidAiTaskResult() {
        InvalidAiTaskResultException ex = new InvalidAiTaskResultException("Invalid result format");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidAiTaskResult(
                ex, mockRequest("/api/v1/ai-tasks/result"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Invalid result format", body.message());
        assertEquals("/api/v1/ai-tasks/result", body.path());
    }

    @Test
    void shouldHandleEntityNotFound() {
        EntityNotFoundException ex = new EntityNotFoundException("Analysis", "abc-123");

        ResponseEntity<ApiErrorResponse> response = handler.handleEntityNotFound(
                ex, mockRequest("/api/v1/analyses/abc-123"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertTrue(body.message().contains("Analysis"));
        assertTrue(body.message().contains("abc-123"));
        assertEquals("/api/v1/analyses/abc-123", body.path());
    }

    @Test
    void shouldHandleConflict() {
        ConflictException ex = new ConflictException("Resource version mismatch");

        ResponseEntity<ApiErrorResponse> response = handler.handleConflict(
                ex, mockRequest("/api/v1/decisions/1"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Resource version mismatch", body.message());
        assertEquals("/api/v1/decisions/1", body.path());
    }

    @Test
    void shouldHandleValidationErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "name", "must not be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(
                ex, mockRequest("/api/v1/projects"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("must not be blank", body.message());
        assertEquals("/api/v1/projects", body.path());
    }

    @Test
    void shouldHandleValidationWithNoFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(
                ex, mockRequest("/api/v1/projects"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.message());
    }

    @Test
    void shouldHandleUnexpectedException() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                ex, mockRequest("/api/v1/unknown"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("An unexpected error occurred.", body.message());
        assertEquals("/api/v1/unknown", body.path());
    }

    @Test
    void shouldReturnTimestampOnAllResponses() {
        EntityNotFoundException ex = new EntityNotFoundException("Test", "1");

        ResponseEntity<ApiErrorResponse> response = handler.handleEntityNotFound(
                ex, mockRequest("/test"));

        assertNotNull(response.getBody().timestamp());
    }
}
