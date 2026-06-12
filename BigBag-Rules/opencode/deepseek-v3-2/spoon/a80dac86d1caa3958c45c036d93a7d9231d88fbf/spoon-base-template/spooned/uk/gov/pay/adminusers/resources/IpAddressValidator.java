package uk.gov.pay.adminusers.resources;
public class IpAddressValidator implements javax.validation.ConstraintValidator<uk.gov.pay.adminusers.resources.ValidIpAddress, java.lang.Object> {
    @java.lang.Override
    public void initialize(uk.gov.pay.adminusers.resources.ValidIpAddress constraintAnnotation) {
    }

    @java.lang.Override
    public boolean isValid(java.lang.Object value, javax.validation.ConstraintValidatorContext context) {
        // it is considered best practice to return true if value is null and use the @NotNull constraint where
        // necessary
        return (value == null) || org.apache.commons.validator.routines.InetAddressValidator.getInstance().isValid(value.toString());
    }
}
