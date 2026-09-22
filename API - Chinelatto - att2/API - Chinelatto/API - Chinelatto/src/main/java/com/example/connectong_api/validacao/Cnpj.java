package com.example.connectong_api.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** CNPJ com digitos verificadores (numerico ou alfanumerico). Regra em {@link Regras#cnpj(String)}. */
@Documented
@Constraint(validatedBy = Cnpj.Validador.class)
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cnpj {

    String message() default "CNPJ inválido: confira os 14 caracteres e os dígitos verificadores";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Cnpj, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext ctx) {
            return Regras.cnpj(valor);
        }
    }
}
