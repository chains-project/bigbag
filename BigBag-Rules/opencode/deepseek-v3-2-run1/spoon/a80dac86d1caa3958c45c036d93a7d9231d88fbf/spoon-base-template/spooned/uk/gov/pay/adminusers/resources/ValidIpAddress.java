package uk.gov.pay.adminusers.resources;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.FIELD, java.lang.annotation.ElementType.ANNOTATION_TYPE })
@java.lang.annotation.Documented
@javax.validation.Constraint(validatedBy = uk.gov.pay.adminusers.resources.IpAddressValidator.class)
public @interface ValidIpAddress {
    java.lang.String message() default "must be valid IP address";

    java.lang.Class<?>[] groups() default {  };

    java.lang.Class<? extends javax.validation.Payload>[] payload() default {  };
}
