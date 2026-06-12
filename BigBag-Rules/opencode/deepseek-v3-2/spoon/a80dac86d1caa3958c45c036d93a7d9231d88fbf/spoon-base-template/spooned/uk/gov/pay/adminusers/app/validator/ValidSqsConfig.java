package uk.gov.pay.adminusers.app.validator;
@java.lang.annotation.Target({ java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.ANNOTATION_TYPE })
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@javax.validation.Constraint(validatedBy = { uk.gov.pay.adminusers.app.validator.SqsConfigValidator.class })
@java.lang.annotation.Documented
public @interface ValidSqsConfig {
    java.lang.String message() default "{uk.gov.pay.adminusers.app.validator.sqsConfig.message}";

    java.lang.Class<?>[] groups() default {  };

    java.lang.Class<? extends javax.validation.Payload>[] payload() default {  };
}
