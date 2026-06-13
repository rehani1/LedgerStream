package com.ledgerstream.web;

import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(this::formatFieldError)
			.collect(Collectors.joining("; "));
		return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Validation failed", request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ApiErrorResponse> handleMissingParameter(
		MissingServletRequestParameterException ex,
		HttpServletRequest request
	) {
		return build(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), request);
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
		return build(status, message, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, "Resource not found", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
		HttpRequestMethodNotSupportedException ex,
		HttpServletRequest request
	) {
		return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, "Access denied", request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
	}

	private String formatFieldError(FieldError error) {
		String defaultMessage = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
		return error.getField() + " " + defaultMessage;
	}

	private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ApiErrorResponse body = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			status.getReasonPhrase(),
			message,
			request.getRequestURI(),
			currentRequestId(request)
		);
		return ResponseEntity.status(status).body(body);
	}

	private String currentRequestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return requestId == null ? null : requestId.toString();
	}
}
