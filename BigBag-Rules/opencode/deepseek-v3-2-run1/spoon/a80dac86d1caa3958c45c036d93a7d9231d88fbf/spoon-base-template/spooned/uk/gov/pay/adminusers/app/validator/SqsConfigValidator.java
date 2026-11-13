package uk.gov.pay.adminusers.app.validator;
public class SqsConfigValidator implements javax.validation.ConstraintValidator<uk.gov.pay.adminusers.app.validator.ValidSqsConfig, uk.gov.pay.adminusers.app.config.SqsConfig> {
    @java.lang.Override
    public boolean isValid(uk.gov.pay.adminusers.app.config.SqsConfig sqsConfig, javax.validation.ConstraintValidatorContext constraintValidatorContext) {
        if (sqsConfig.isNonStandardServiceEndpoint()) {
            boolean isInvalidEndpointConfig = (org.apache.commons.lang3.StringUtils.isEmpty(sqsConfig.getEndpoint()) || org.apache.commons.lang3.StringUtils.isEmpty(sqsConfig.getSecretKey())) || org.apache.commons.lang3.StringUtils.isEmpty(sqsConfig.getAccessKey());
            if (isInvalidEndpointConfig) {
                constraintValidatorContext.disableDefaultConstraintViolation();
                constraintValidatorContext.buildConstraintViolationWithTemplate("[endpoint, secretKey, accessKey] fields must be set, when `nonStandardServiceEndpoint` is true").addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
