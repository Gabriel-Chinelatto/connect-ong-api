package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** Senha nova com 8+ caracteres, letra e numero, fora das mais comuns. Regra em {@link Regras#senhaForte(String)}. */
@Documented
@Constraint(validatedBy = SenhaForte.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {

    String message() default "A senha deve ter ao menos 8 caracteres, com letras e números (e não pode ser uma senha comum)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SenhaForte, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.senhaForte(valor);
        }
    }
}
