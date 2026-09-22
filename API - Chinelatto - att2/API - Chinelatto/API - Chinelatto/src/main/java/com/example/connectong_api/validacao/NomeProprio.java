package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Nome de pessoa ou cidade: so letras, espaco, hifen, apostrofo e ponto. Regra em {@link Regras#nomeProprio(String)}. */
@Documented
@Constraint(validatedBy = NomeProprio.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NomeProprio {

    String message() default "Use apenas letras (sem números ou símbolos)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<NomeProprio, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.nomeProprio(valor);
        }
    }
}
