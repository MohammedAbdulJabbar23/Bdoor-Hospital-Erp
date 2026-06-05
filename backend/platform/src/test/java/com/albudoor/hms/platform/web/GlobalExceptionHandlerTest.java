package com.albudoor.hms.platform.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the new {@link GlobalExceptionHandler} mappings. Each handler is invoked
 * directly with a constructed exception — no Spring context, no web layer — so the assertions are
 * deterministic and fast. The contract under test: framework/persistence exceptions map to the
 * correct HTTP status and stable {@code ApiError.code}, and generic messages never echo
 * {@code ex.getMessage()} (which would leak SQL, enum lists, and Hibernate internals).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLock_maps_to_409_concurrentModification() {
        ResponseEntity<ApiError> res =
                handler.handleOptimisticLock(new OptimisticLockingFailureException("row version 3 != 4"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().status()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(res.getBody().message()).doesNotContain("row version");
    }

    @Test
    void ormObjectOptimisticLock_is_covered_by_superclass_handler() {
        // ObjectOptimisticLockingFailureException extends OptimisticLockingFailureException,
        // so the single superclass handler covers it.
        var ex = new ObjectOptimisticLockingFailureException("Bed", UUID.randomUUID());
        ResponseEntity<ApiError> res = handler.handleOptimisticLock(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");
    }

    @Test
    void httpMessageNotReadable_maps_to_400_malformedRequest_withoutLeakingParserMessage() {
        // A real Jackson InvalidFormatException message lists the accepted enum values; ensure it is suppressed.
        String leaky = "not one of the values accepted for Enum class: [MALE, FEMALE, OTHER]";
        ResponseEntity<ApiError> res =
                handler.handleNotReadable(new HttpMessageNotReadableException(leaky));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().status()).isEqualTo(400);
        assertThat(res.getBody().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(res.getBody().message()).doesNotContain("MALE", "FEMALE", "Enum");
    }

    @Test
    void typeMismatch_maps_to_400_invalidParameter() {
        var ex = new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", null, new IllegalArgumentException("bad uuid"));
        ResponseEntity<ApiError> res = handler.handleTypeMismatch(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().status()).isEqualTo(400);
        assertThat(res.getBody().code()).isEqualTo("INVALID_PARAMETER");
        assertThat(res.getBody().message()).doesNotContain("not-a-uuid");
    }

    @Test
    void missingRequestParameter_maps_to_400_missingParameter_andNamesTheParam() {
        // e.g. GET /api/dept-cases without the required `category` query param.
        var ex = new MissingServletRequestParameterException("category", "DepartmentCategory");
        ResponseEntity<ApiError> res = handler.handleMissingParam(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().status()).isEqualTo(400);
        assertThat(res.getBody().code()).isEqualTo("MISSING_PARAMETER");
        // The parameter name is safe to echo and helps the caller fix the request.
        assertThat(res.getBody().message()).contains("category");
    }

    @Test
    void dataIntegrity_maps_to_409_constraintViolation() {
        String leaky = "ERROR: insert violates foreign key constraint \"fk_visit_patient\"";
        ResponseEntity<ApiError> res =
                handler.handleDataIntegrity(new DataIntegrityViolationException(leaky));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().status()).isEqualTo(409);
        assertThat(res.getBody().code()).isEqualTo("CONSTRAINT_VIOLATION");
        assertThat(res.getBody().message()).doesNotContain("foreign key", "fk_visit_patient");
    }

    @Test
    void illegalArgument_maps_to_400_invalidRequest() {
        // e.g. Spring Data PageRequest.of(-1, 20) throws IllegalArgumentException.
        ResponseEntity<ApiError> res =
                handler.handleIllegalArgument(new IllegalArgumentException("Page index must not be less than zero"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().status()).isEqualTo(400);
        assertThat(res.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(res.getBody().message()).doesNotContain("Page index");
    }

    @Test
    void handleAny_maps_to_500_generic_withoutLeakingExceptionMessage() {
        String leaky = "could not extract ResultSet; SQL [select * from payment where ...]";
        ResponseEntity<ApiError> res = handler.handleAny(new RuntimeException(leaky));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().status()).isEqualTo(500);
        assertThat(res.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(res.getBody().message()).isEqualTo("An unexpected error occurred.");
        assertThat(res.getBody().message()).doesNotContain("SQL", "ResultSet");
    }
}
