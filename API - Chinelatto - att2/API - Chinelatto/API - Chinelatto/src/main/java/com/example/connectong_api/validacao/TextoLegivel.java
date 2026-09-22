package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Texto livre com ao menos 2 letras e sem HTML. Regra em {@link Regras#textoLegivel(String)}. */
@Documented
@Constraint(validatedBy = TextoLegivel.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TextoLegivel {

    String message() default "Texto inválido: escreva com letras e sem os caracteres < e >";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<TextoLegivel, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.textoLegivel(valor);
        }
    }
}
