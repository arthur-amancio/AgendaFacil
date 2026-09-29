package br.com.agendafacilpro.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import br.com.agendafacilpro.service.BookingConflictException;
import br.com.agendafacilpro.service.InvalidRequestException;
import br.com.agendafacilpro.service.ResourceNotFoundException;
import br.com.agendafacilpro.service.StateConflictException;
import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE =
            "Tente novamente. Se continuar acontecendo, fale com o suporte técnico.";

    @ExceptionHandler(InvalidRequestException.class)
    ModelAndView invalidRequest(InvalidRequestException exception) {
        return error(HttpStatus.BAD_REQUEST, "Verifique os dados", exception.publicMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ModelAndView notFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Página não encontrada", exception.publicMessage());
    }

    @ExceptionHandler(BookingConflictException.class)
    ModelAndView bookingConflict(BookingConflictException exception) {
        return error(HttpStatus.CONFLICT, "Horário indisponível", exception.publicMessage());
    }

    @ExceptionHandler(StateConflictException.class)
    ModelAndView stateConflict(StateConflictException exception) {
        return error(HttpStatus.CONFLICT, "Não foi possível concluir", exception.publicMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    ModelAndView validation(Exception exception) {
        String message = PublicValidationMessages.GENERIC_MESSAGE;
        if (exception instanceof BindException bind) {
            message = PublicValidationMessages.from(bind.getBindingResult());
        } else if (exception instanceof MethodArgumentNotValidException invalid) {
            message = PublicValidationMessages.from(invalid.getBindingResult());
        } else if (exception instanceof ConstraintViolationException violation) {
            message = PublicValidationMessages.from(violation);
        }
        return error(HttpStatus.BAD_REQUEST, "Verifique os dados", message);
    }

    @ExceptionHandler({TypeMismatchException.class, ServletRequestBindingException.class})
    ModelAndView malformedRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Verifique os dados", PublicValidationMessages.GENERIC_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    ModelAndView unexpected(Exception exception) {
        log.error("Erro inesperado", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Não conseguimos concluir essa ação", INTERNAL_ERROR_MESSAGE);
    }

    private ModelAndView error(HttpStatus status, String title, String message) {
        ModelAndView response = new ModelAndView("error");
        response.setStatus(status);
        response.addObject("title", title);
        response.addObject("message", message);
        return response;
    }
}
