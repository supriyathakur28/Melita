package com.melita.orders.platform.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

/**
 * The single place where exceptions become HTTP responses. Every error is {@code application/problem+json}
 * with a stable {@code code} and the {@code requestId}; internals never leak into a response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = Problems.of(ex.status(), ex.code(), ex.status().getReasonPhrase(),
                ex.getMessage(), ex.violations());
        ex.properties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.status()).body(problem);
    }

    /** {@code @PreAuthorize} failures inside a controller reach MVC rather than the security filter chain. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SecurityProblems.forbidden());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SecurityProblems.unauthorized());
    }

    /**
     * Someone else changed the same row first ({@code @Version}), or a lock could not be obtained (lock wait
     * timeout, deadlock victim): the client should re-read and try again.
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrentUpdate(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.of(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Conflict", "The resource was changed by another request. Reload it and try again."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), null, v.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return validationFailed(errors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
                "An unexpected error occurred. Quote the requestId when contacting support."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof org.springframework.validation.FieldError fieldError
                        ? new FieldViolation(fieldError.getField(), fieldError.getCode(), fieldError.getDefaultMessage())
                        : new FieldViolation(error.getObjectName(), error.getCode(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return ResponseEntity.badRequest().body(validationFailed(errors).getBody());
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                             HttpHeaders headers,
                                                                             HttpStatusCode status,
                                                                             WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::violations)
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        return ResponseEntity.badRequest().body(validationFailed(errors).getBody());
    }

    /**
     * When a controller method has a constraint on a plain parameter (e.g. a header), Spring validates the whole
     * method at once, and errors in a {@code @Valid} body arrive here as {@link ParameterErrors}: report them by
     * field path, exactly as body validation does.
     */
    private static java.util.stream.Stream<FieldViolation> violations(ParameterValidationResult result) {
        if (result instanceof ParameterErrors bodyErrors) {
            return bodyErrors.getAllErrors().stream().map(error ->
                    error instanceof org.springframework.validation.FieldError fieldError
                            ? new FieldViolation(fieldError.getField(), fieldError.getCode(), fieldError.getDefaultMessage())
                            : new FieldViolation(error.getObjectName(), error.getCode(), error.getDefaultMessage()));
        }
        return result.getResolvableErrors().stream()
                .map(error -> new FieldViolation(requestName(result.getMethodParameter()), null, error.getDefaultMessage()));
    }

    /** A required header that is absent is a validation error on that header, like any other field. */
    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing) {
            return ResponseEntity.badRequest().body(validationFailed(
                    List.of(new FieldViolation(missing.getHeaderName(), null, "is required"))).getBody());
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    /** The name the client used: the header or query parameter name, not the Java parameter name. */
    private static String requestName(MethodParameter parameter) {
        final RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        final RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        return parameter.getParameterName();
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest().body(Problems.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Malformed request", "The request body is not valid JSON or contains unknown or mistyped fields."));
    }

    /** Framework exceptions (405, 415, missing header, ...) keep their status but get our problem shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() != null
                && problem.getProperties().containsKey("code")) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }
        String detail = ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null
                ? errorResponse.getBody().getDetail()
                : "The request could not be processed.";
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status == null ? "REQUEST_ERROR" : status.name();
        String title = status == null ? "Request error" : status.getReasonPhrase();
        return super.handleExceptionInternal(ex, Problems.of(statusCode, code, title, detail), headers,
                statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> validationFailed(List<FieldViolation> errors) {
        return ResponseEntity.badRequest().body(Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", errors.size() + " field(s) are invalid", errors));
    }
}
