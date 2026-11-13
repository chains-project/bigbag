package uk.gov.pay.adminusers.resources;
public class ServiceRequestValidator {
    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_NAME = "name";

    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_LINE1 = "address_line1";

    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_CITY = "address_city";

    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE = "address_postcode";

    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY = "address_country";

    /* default */
    static final java.lang.String FIELD_MERCHANT_DETAILS_EMAIL = "email";

    public static final java.lang.String SERVICE_SEARCH_SUPPORT_ERR_MSG = "Search only supports searching by service name or merchant name";

    public static final java.lang.String SERVICE_SEARCH_LENGTH_ERR_MSG = "Search strings can only be 60 characters or less";

    public static final java.lang.String SERVICE_SEARCH_SPECIAL_CHARS_ERR_MSG = "Search strings can only contain letters, numbers and spaces";

    private static final int FIELD_MERCHANT_DETAILS_NAME_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_EMAIL_MAX_LENGTH = 255;

    private static final int MAX_SEARCH_STRING_LENGTH = 60;

    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    private final uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator serviceUpdateOperationValidator;

    @javax.inject.Inject
    public ServiceRequestValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator serviceUpdateOperationValidator) {
        this.requestValidations = requestValidations;
        this.serviceUpdateOperationValidator = serviceUpdateOperationValidator;
    }

    /* default */
    java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateUpdateAttributeRequest(com.fasterxml.jackson.databind.JsonNode payload) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        if (payload.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode updateOperation : payload) {
                errors.addAll(serviceUpdateOperationValidator.validate(updateOperation));
            }
        } else {
            errors = serviceUpdateOperationValidator.validate(payload);
        }
        if (!errors.isEmpty()) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(errors));
        }
        return java.util.Optional.empty();
    }

    /* default */
    void validateUpdateMerchantDetailsRequest(com.fasterxml.jackson.databind.JsonNode payload) throws uk.gov.pay.adminusers.exception.ValidationException {
        java.util.Optional<java.util.List<java.lang.String>> missingMandatoryFieldErrors = requestValidations.checkExistsAndNotEmpty(payload, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE1, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY);
        if (missingMandatoryFieldErrors.isPresent()) {
            throw new uk.gov.pay.adminusers.exception.ValidationException(uk.gov.pay.adminusers.utils.Errors.from(missingMandatoryFieldErrors.get()));
        }
        java.util.Optional<java.util.List<java.lang.String>> invalidLengthFieldErrors = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME_MAX_LENGTH, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_NAME);
        if (invalidLengthFieldErrors.isPresent()) {
            throw new uk.gov.pay.adminusers.exception.ValidationException(uk.gov.pay.adminusers.utils.Errors.from(invalidLengthFieldErrors.get()));
        }
        if (payload.has(uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL)) {
            validateMerchantEmail(payload);
        }
    }

    private void validateMerchantEmail(com.fasterxml.jackson.databind.JsonNode payload) throws uk.gov.pay.adminusers.exception.ValidationException {
        java.util.Optional<java.util.List<java.lang.String>> errors;
        errors = requestValidations.checkMaxLength(payload, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL_MAX_LENGTH, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL);
        if (errors.isPresent()) {
            throw new uk.gov.pay.adminusers.exception.ValidationException(uk.gov.pay.adminusers.utils.Errors.from(errors.get()));
        }
        errors = requestValidations.isValidEmail(payload, uk.gov.pay.adminusers.resources.ServiceRequestValidator.FIELD_MERCHANT_DETAILS_EMAIL);
        if (errors.isPresent()) {
            throw new uk.gov.pay.adminusers.exception.ValidationException(uk.gov.pay.adminusers.utils.Errors.from(errors.get()));
        }
    }

    /* default */
    java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateFindRequest(java.lang.String gatewayAccountId) {
        if (org.apache.commons.lang3.StringUtils.isBlank(gatewayAccountId)) {
            return java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from("Find services currently support only by gatewayAccountId"));
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<uk.gov.pay.adminusers.utils.Errors> validateSearchRequest(uk.gov.pay.adminusers.model.ServiceSearchRequest request) {
        var allowedChars = java.util.regex.Pattern.compile("^[0-9A-Za-z\\s]+$");
        var errorList = new java.util.ArrayList<java.lang.String>();
        var values = request.toMap().values().stream().filter(value -> !org.apache.commons.lang3.StringUtils.isBlank(value)).collect(java.util.stream.Collectors.toList());
        if (values.isEmpty()) {
            errorList.add(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SUPPORT_ERR_MSG);
        } else {
            values.forEach(value -> {
                if (lengthValidator(value)) {
                    errorList.add(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_LENGTH_ERR_MSG);
                }
                if (!allowedChars.matcher(value).matches()) {
                    errorList.add(uk.gov.pay.adminusers.resources.ServiceRequestValidator.SERVICE_SEARCH_SPECIAL_CHARS_ERR_MSG);
                }
            });
        }
        return errorList.size() > 0 ? java.util.Optional.of(uk.gov.pay.adminusers.utils.Errors.from(errorList)) : java.util.Optional.empty();
    }

    private boolean lengthValidator(java.lang.String checkValue) {
        return (!org.apache.commons.lang3.StringUtils.isBlank(checkValue)) && (checkValue.length() > uk.gov.pay.adminusers.resources.ServiceRequestValidator.MAX_SEARCH_STRING_LENGTH);
    }
}
