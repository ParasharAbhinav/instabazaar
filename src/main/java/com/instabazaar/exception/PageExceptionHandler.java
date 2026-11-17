package com.instabazaar.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class PageExceptionHandler {
    @ExceptionHandler(DomainException.class) public ModelAndView domain(DomainException exception) { return error(exception.getStatus(), exception.getMessage()); }
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ModelAndView validation(Exception exception) { return error(HttpStatus.BAD_REQUEST, "Some details are invalid. Go back, check the form, and try again."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ModelAndView upload() { return error(HttpStatus.PAYLOAD_TOO_LARGE, "Image uploads must not exceed 5 MB."); }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ModelAndView conflict(Exception exception) { return error(HttpStatus.CONFLICT, "This information was saved or changed by another request. Reload and try again."); }
    @ExceptionHandler(AccessDeniedException.class) public ModelAndView denied() { return error(HttpStatus.FORBIDDEN, "You do not have access to this action."); }

    private ModelAndView error(HttpStatus status, String message) {
        ModelAndView view = new ModelAndView("error"); view.setStatus(status);
        view.addObject("status", status.value()); view.addObject("message", message); return view;
    }
}
