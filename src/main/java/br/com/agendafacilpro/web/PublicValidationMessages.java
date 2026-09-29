package br.com.agendafacilpro.web;

import java.util.Arrays;
import java.util.Set;

import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import jakarta.validation.ConstraintViolationException;

final class PublicValidationMessages {

    static final String GENERIC_MESSAGE = "Verifique os dados e tente novamente.";

    private static final Set<String> CONTROLLED_CONSTRAINTS = Set.of(
            "AssertFalse", "AssertTrue", "DecimalMax", "DecimalMin", "Digits", "Email",
            "Future", "FutureOrPresent", "Max", "Min", "Negative", "NegativeOrZero",
            "NotBlank", "NotEmpty", "NotNull", "Null", "Past", "PastOrPresent", "Pattern",
            "Positive", "PositiveOrZero", "Size"
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
                .map(violation -> violation.getMessage())
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(GENERIC_MESSAGE);
    }

    private static boolean isControlledConstraint(ObjectError error) {
        if (error.getCodes() == null) {
            return false;
        }
        return Arrays.stream(error.getCodes())
                .map(code -> code.contains(".") ? code.substring(0, code.indexOf('.')) : code)
                .anyMatch(CONTROLLED_CONSTRAINTS::contains);
    }
}
