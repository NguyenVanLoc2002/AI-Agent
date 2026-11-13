package com.secureerp.agent.platform.internal;

import com.secureerp.agent.platform.ApplicationException;
import com.secureerp.agent.platform.ErrorCode;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only controller exercising the error/validation contract. Not part of the application. */
@RestController
class ValidationProbeController {

    static final String SECRET_IN_MESSAGE = "987654321";

    record ProbeRequest(@NotBlank @Size(max = 32) String sku, @Positive @Max(999) Integer requestedQuantity) {
    }

    record AttributesRequest(@NotBlank String sku, Map<String, @NotBlank String> attrs) {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = RangeValidator.class)
    @interface ValidRange {
        String message() default "invalid range";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class RangeValidator implements ConstraintValidator<ValidRange, RangeRequest> {
        @Override
        public boolean isValid(RangeRequest value, ConstraintValidatorContext context) {
            return value == null || value.from() == null || value.to() == null || value.from() <= value.to();
        }
    }

    @ValidRange
    record RangeRequest(Integer from, Integer to) {
    }

    @PostMapping("/test-probe/items")
    ProbeRequest create(@Valid @RequestBody ProbeRequest request) {
        return request;
    }

    @PostMapping("/test-probe/attributes")
    AttributesRequest attributes(@Valid @RequestBody AttributesRequest request) {
        return request;
    }

    @PostMapping("/test-probe/range")
    RangeRequest range(@Valid @RequestBody RangeRequest request) {
        return request;
    }

    @GetMapping("/test-probe/count")
    int count(@RequestParam @Positive int limit) {
        return limit;
    }

    @GetMapping("/test-probe/boom")
    String boom() {
        throw new IllegalStateException("cost=" + SECRET_IN_MESSAGE + " for customer CUST-SYNTH-A");
    }

    @GetMapping("/test-probe/app-error")
    String appError() {
        throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE,
                new RuntimeException("debt=" + SECRET_IN_MESSAGE));
    }

    @GetMapping("/test-probe/status/{code}")
    String status(@PathVariable int code) {
        throw new ResponseStatusException(HttpStatus.valueOf(code), "reason with " + SECRET_IN_MESSAGE);
    }
}
