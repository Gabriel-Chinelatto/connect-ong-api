package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Sigla ou nome de um dos 27 estados. Regra em {@link Regras#uf(String)}. */
@Documented
@Constraint(validatedBy = Uf.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Uf {

    String message() default "Estado inválido: use a sigla (ex.: SP) ou o nome do estado";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Uf, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.uf(valor);
        }
    }
}
