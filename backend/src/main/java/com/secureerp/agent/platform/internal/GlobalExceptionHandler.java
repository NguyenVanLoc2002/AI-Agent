package com.secureerp.agent.platform.internal;

import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps every exception to an RFC 9457 problem response with a stable {@link ErrorCode}.
 *
 * <p>Security (rule 10, rule 20, ADR-012): responses and logs never contain exception messages,
 * stack traces, rejected values or request bodies. Validation failures expose only field names
 * (with index/map-key content removed) and constraint names. Logs contain the error code, the
 * exception class chain and the trace ID.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Removes user-controlled content such as {@code attrs[<user key>]} from property paths. */
    private static final Pattern INDEX_CONTENT = Pattern.compile("\\[[^\\]]*\\]");

    private static final Comparator<Map<String, String>> FIELD_ORDER =
            Comparator.comparing((Map<String, String> m) -> m.get("field")).thenComparing(m -> m.get("constraint"));

    /**
     * Authorization/authentication failures must reach Spring Security's ExceptionTranslationFilter
     * (401/403 handling) instead of being swallowed as 500. Matched by name because Spring Security
     * is not yet on the classpath (added with ADR-010).
     */
    static final Set<String> SECURITY_EXCEPTIONS = Set.of(
            "org.springframework.security.access.AccessDeniedException",
            "org.springframework.security.core.AuthenticationException");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> onInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return validationProblem(violations(ex.getBindingResult()), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> onInvalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<Map<String, String>> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> entry(
                        Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter"),
                        constraintName(error.getCodes()))))
                .sorted(FIELD_ORDER)
                .toList();
        return validationProblem(fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> onConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> fields = ex.getConstraintViolations().stream()
                .map(v -> entry(v.getPropertyPath().toString(),
                        v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()))
                .sorted(FIELD_ORDER)
                .toList();
        return validationProblem(fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ProblemDetail> onUnreadable(Exception ex, HttpServletRequest request) {
        return problem(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.status(), ex, request);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ProblemDetail> onApplicationException(ApplicationException ex, HttpServletRequest request) {
        return problem(ex.code(), ex.code().status(), ex, request);
    }

    /**
     * Fallback. Framework exceptions that carry an HTTP status (404, 405, 409, 415, ...) implement
     * {@link ErrorResponse}; their status is kept, their own body/detail is discarded.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> onUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (isSecurityException(ex)) {
            throw ex;
        }
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return problem(codeFor(status), status, ex, request);
        }
        return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.status(), ex, request);
    }

    static boolean isSecurityException(Throwable ex) {
        for (Class<?> type = ex.getClass(); type != null; type = type.getSuperclass()) {
            if (SECURITY_EXCEPTIONS.contains(type.getName())) {
                return true;
            }
        }
        return false;
    }

    static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 409 -> ErrorCode.CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCode.REQUEST_FAILED : ErrorCode.INTERNAL_ERROR;
        };
    }

    static String sanitizePath(String path) {
        return INDEX_CONTENT.matcher(path).replaceAll("[]");
    }

    /** Class names of the exception and its causes, never messages. */
    static String causeChain(Throwable ex) {
        List<String> names = new ArrayList<>();
        for (Throwable t = ex; t != null && names.size() < 5; t = t.getCause()) {
            names.add(t.getClass().getName());
            if (t.getCause() == t) {
                break;
            }
        }
        return String.join(" <- ", names);
    }

    private ResponseEntity<ProblemDetail> validationProblem(List<Map<String, String>> fields, HttpServletRequest request) {
        ProblemDetail body = baseProblem(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.status(), request);
        body.setProperty("fields", fields);
        log.info("request_rejected code={} fieldCount={}", ErrorCode.VALIDATION_FAILED, fields.size());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    private ResponseEntity<ProblemDetail> problem(ErrorCode code, HttpStatusCode status, Exception ex,
            HttpServletRequest request) {
        if (status.is5xxServerError()) {
            // Deliberately no message/stack: they may contain sensitive values (ADR-012).
            log.error("request_failed code={} status={} exception={}", code, status.value(), causeChain(ex));
        } else {
            log.info("request_rejected code={} status={} exception={}", code, status.value(), ex.getClass().getName());
        }
        return ResponseEntity.status(status).body(baseProblem(code, status, request));
    }

    private static ProblemDetail baseProblem(ErrorCode code, HttpStatusCode status, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setType(URI.create("urn:secure-erp-agent:problem:" + code.name().toLowerCase().replace('_', '-')));
        body.setTitle(code.title());
        body.setProperty("code", code.name());
        Object traceId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (traceId != null) {
            body.setProperty("traceId", traceId.toString());
        }
        return body;
    }

    private static List<Map<String, String>> violations(BindingResult result) {
        List<Map<String, String>> fields = new ArrayList<>();
        result.getFieldErrors().forEach(e -> fields.add(entry(e.getField(), constraintName(e.getCodes()))));
        // Class-level constraints have no field; report them without echoing the object.
        result.getGlobalErrors().forEach(e -> fields.add(entry("(object)", constraintName(e.getCodes()))));
        fields.sort(FIELD_ORDER);
        return fields;
    }

    private static Map<String, String> entry(String field, String constraint) {
        return Map.of("field", sanitizePath(field), "constraint", constraint);
    }

    /** Last resolvable code is the bare constraint name, e.g. {@code NotBlank}. Never the message. */
    private static String constraintName(String[] codes) {
        if (codes == null || codes.length == 0) {
            return "Invalid";
        }
        return codes[codes.length - 1];
    }
}
