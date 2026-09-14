package com.qrmenu.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Thrown by Spring's multipart resolver before the controller method runs (spring.servlet.multipart.max-file-size). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Uploaded file exceeds the maximum allowed size.");
    }

    @ExceptionHandler({
        ProductNotOrderableException.class,
        OrderingNotAllowedException.class,
        BranchHasActiveOrdersException.class,
        LastActiveBusinessAdminException.class,
        CategoryHasProductsException.class,
        TableHasVisitHistoryException.class,
        TableInUseException.class,
        DuplicateEmailException.class,
        DuplicateExpenseCategoryNameException.class,
        DuplicateTableLabelException.class
    })
    public ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TableVisitExpiredException.class)
    public ProblemDetail handleGone(TableVisitExpiredException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
    }

    /** Distinct status from the 409s above so customer-web can tell "temporarily closed" apart from "deactivated". */
    @ExceptionHandler(BusinessUnavailableException.class)
    public ProblemDetail handleServiceUnavailable(BusinessUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler({InvalidWebhookSignatureException.class, StaffAuthenticationRequiredException.class})
    public ProblemDetail handleUnauthorized(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(StaffPermissionDeniedException.class)
    public ProblemDetail handleForbidden(StaffPermissionDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
