package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Explicit callback validation for paths that do not go through Spring MVC validation. */
final class AiTaskResultCallbackContractValidator {
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private AiTaskResultCallbackContractValidator() {
    }

    static void validateInteractionTraces(List<AiInteractionTraceRequest> traces) {
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < traces.size(); index++) {
            final int traceIndex = index;
            AiInteractionTraceRequest trace = traces.get(index);
            if (trace == null) {
                violations.add("interactionTraces[" + traceIndex + "] must not be null");
                continue;
            }
            VALIDATOR.validate(trace).stream()
                    .sorted(Comparator.comparing((ConstraintViolation<AiInteractionTraceRequest> violation) ->
                                    violation.getPropertyPath().toString())
                            .thenComparing(ConstraintViolation::getMessage))
                    .map(violation -> "interactionTraces[" + traceIndex + "]."
                            + violation.getPropertyPath() + " " + violation.getMessage())
                    .forEach(violations::add);
        }
        if (!violations.isEmpty()) {
            throw new InvalidAiTaskResultException(
                    "AI task result callback contains invalid interaction traces: "
                            + String.join("; ", violations));
        }
    }
}
