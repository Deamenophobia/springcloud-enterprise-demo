package com.example.commerce.orders;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OrderController.class)
@Order(-1)
class OrderValidationAdvice {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidOrderBody(MethodArgumentNotValidException exception) {
        var problem = exception.getBody();
        var errors = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getCode(), error.getDefaultMessage()))
            .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    private record FieldViolation(String field, String code, String message) {}
}
