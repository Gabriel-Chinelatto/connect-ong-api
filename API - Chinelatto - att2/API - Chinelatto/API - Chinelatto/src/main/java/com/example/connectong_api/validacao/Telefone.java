package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Telefone brasileiro com DDD, fixo ou celular. Regra em {@link Regras#telefone(String)}. */
@Documented
@Constraint(validatedBy = Telefone.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Telefone {

    String message() default "Telefone inválido: use DDD + número, ex.: (19) 99876-5432";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Telefone, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.telefone(valor);
        }
    }
}
