package com.ssa.lms.admission.web;

import com.ssa.lms.admission.service.AdmissionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PublicAdmissionController.class)
public class PublicAdmissionExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PublicAdmissionError> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("입력 내용을 확인해 주세요.");
        return ResponseEntity.badRequest().body(new PublicAdmissionError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler({AdmissionException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<PublicAdmissionError> rejected(Exception exception) {
        String message = exception instanceof AdmissionException
                ? exception.getMessage() : "입력 내용을 확인해 주세요.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new PublicAdmissionError("SUBMISSION_REJECTED", message));
    }
}
