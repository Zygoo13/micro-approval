package com.microapproval.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ");
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail handleForbidden(ForbiddenOperationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(InvalidOperationException.class)
    ProblemDetail handleInvalidOperation(InvalidOperationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
