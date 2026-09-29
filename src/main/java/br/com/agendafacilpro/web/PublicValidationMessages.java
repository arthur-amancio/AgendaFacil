package br.com.agendafacilpro.web;

import java.lang.annotation.Annotation;
import java.util.Set;

import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

final class PublicValidationMessages {

    static final String GENERIC_MESSAGE = "Verifique os dados e tente novamente.";

    private static final Set<Class<? extends Annotation>> CONTROLLED_CONSTRAINTS = Set.of(
            AssertFalse.class, AssertTrue.class, DecimalMax.class, DecimalMin.class, Digits.class,
            Email.class, Future.class, FutureOrPresent.class, Max.class, Min.class, Negative.class,
            NegativeOrZero.class, NotBlank.class, NotEmpty.class, NotNull.class, Null.class,
            Past.class, PastOrPresent.class, Pattern.class, Positive.class, PositiveOrZero.class,
            Size.class
    );

    private PublicValidationMessages() {
    }

    static String from(BindingResult binding) {
        return binding.getAllErrors().stream()
                .filter(PublicValidationMessages::isControlledConstraint)
                .map(ObjectError::getDefaultMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(GENERIC_MESSAGE);
    }

    static String from(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .filter(PublicValidationMessages::isControlledConstraint)
                .map(violation -> violation.getMessage())
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(GENERIC_MESSAGE);
    }

    private static boolean isControlledConstraint(ObjectError error) {
        if (!error.contains(ConstraintViolation.class)) {
            return false;
        }
        return isControlledConstraint(error.unwrap(ConstraintViolation.class));
    }

    private static boolean isControlledConstraint(ConstraintViolation<?> violation) {
        Class<? extends Annotation> annotationType = violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType();
        return CONTROLLED_CONSTRAINTS.contains(annotationType);
    }
}
