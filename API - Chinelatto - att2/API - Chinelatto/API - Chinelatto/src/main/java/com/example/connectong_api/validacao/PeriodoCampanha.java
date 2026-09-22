package com.example.connectong_api.validacao;

import com.example.connectong_api.dto.CampanhaRequestDTO;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * A campanha nao pode terminar antes de comecar, nem durar mais de 2 anos.
 * O erro e preso ao campo {@code dataFim}, para sair no mesmo formato
 * ({@code campos.dataFim}) que os apps ja mostram embaixo do campo.
 */
@Documented
@Constraint(validatedBy = PeriodoCampanha.Validador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeriodoCampanha {

    String message() default "A data de término deve ser igual ou posterior à data de início (e no máximo 2 anos depois)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<PeriodoCampanha, CampanhaRequestDTO> {
        @Override
        public boolean isValid(CampanhaRequestDTO dto, ConstraintValidatorContext ctx) {
            if (dto == null || dto.getDataInicio() == null || dto.getDataFim() == null) return true;
            boolean ok = !dto.getDataFim().isBefore(dto.getDataInicio())
                    && !dto.getDataFim().isAfter(dto.getDataInicio().plusYears(2));
            if (!ok) {
                ctx.disableDefaultConstraintViolation();
                ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("dataFim").addConstraintViolation();
            }
            return ok;
        }
    }
}
