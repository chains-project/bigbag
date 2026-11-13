package uk.gov.pay.adminusers.resources;
public class ServiceUpdateOperationValidator {
    private static final java.lang.String REPLACE = "replace";

    private static final java.lang.String ADD = "add";

    private static final int SERVICE_NAME_MAX_LENGTH = 50;

    private static final int FIELD_MERCHANT_DETAILS_NAME_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_ADDRESS_CITY_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY_CODE_MAX_LENGTH = 10;

    private static final int FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE_MAX_LENGTH = 25;

    private static final int FIELD_MERCHANT_DETAILS_EMAIL_MAX_LENGTH = 255;

    private static final int FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER_MAX_LENGTH = 255;

    private static final int FIELD_SECTOR_MAX_LENGTH = 50;

    private final java.util.Map<java.lang.String, java.util.List<java.lang.String>> validAttributeUpdateOperations;

    private final uk.gov.pay.adminusers.validations.RequestValidations requestValidations;

    private static final java.util.EnumSet<uk.gov.pay.adminusers.model.GoLiveStage> GO_LIVE_STAGES = java.util.EnumSet.allOf(uk.gov.pay.adminusers.model.GoLiveStage.class);

    private static final java.util.EnumSet<uk.gov.pay.adminusers.model.PspTestAccountStage> PSP_TEST_ACCOUNT_STAGES = java.util.EnumSet.allOf(uk.gov.pay.adminusers.model.PspTestAccountStage.class);

    @javax.inject.Inject
    public ServiceUpdateOperationValidator(uk.gov.pay.adminusers.validations.RequestValidations requestValidations) {
        java.util.Map<java.lang.String, java.util.List<java.lang.String>> validAttributeUpdateOperations = new java.util.HashMap<>(java.util.Map.ofEntries(java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_GATEWAY_ACCOUNT_IDS, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.ADD)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CUSTOM_BRANDING, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_REDIRECT_NAME, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_EXPERIMENTAL_FEATURES_ENABLED, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_AGENT_INITIATED_MOTO_ENABLED, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_COLLECT_BILLING_ADDRESS, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_DEFAULT_BILLING_ADDRESS_COUNTRY, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_GO_LIVE_STAGE, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_PSP_TEST_ACCOUNT_STAGE, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SECTOR, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_INTERNAL, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_ARCHIVED, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_WENT_LIVE_DATE, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_NAME, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_CITY, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_COUNRTY, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_EMAIL, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)), java.util.Map.entry(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_URL, java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE))));
        java.util.Arrays.stream(uk.gov.service.payments.commons.model.SupportedLanguage.values()).forEach(lang -> validAttributeUpdateOperations.put((uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SERVICE_NAME_PREFIX + '/') + lang.toString(), java.util.Collections.singletonList(uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.REPLACE)));
        this.validAttributeUpdateOperations = java.util.Map.copyOf(validAttributeUpdateOperations);
        this.requestValidations = requestValidations;
    }

    /* default */
    java.util.List<java.lang.String> validate(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = validateOpAndPathExistAndNotEmpty(operation);
        if (!errors.isEmpty()) {
            return errors;
        }
        errors = validateValueIsValidForPath(operation);
        if (!errors.isEmpty()) {
            return errors;
        }
        errors = validateOperationIsValidForPath(operation);
        if (!errors.isEmpty()) {
            return errors;
        }
        return java.util.Collections.emptyList();
    }

    private java.util.List<java.lang.String> validateOpAndPathExistAndNotEmpty(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        requestValidations.checkExistsAndNotEmpty(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_OP, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_PATH).ifPresent(errors::addAll);
        return errors;
    }

    private java.util.List<java.lang.String> validateValueIsValidForPath(com.fasterxml.jackson.databind.JsonNode operation) {
        java.lang.String path = operation.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_PATH).asText();
        if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CUSTOM_BRANDING.equals(path)) {
            return validateCustomBrandingValue(operation);
        } else if (path.startsWith(uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SERVICE_NAME_PREFIX)) {
            return validateServiceNameValue(operation, path);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_REDIRECT_NAME.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_EXPERIMENTAL_FEATURES_ENABLED.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_AGENT_INITIATED_MOTO_ENABLED.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_COLLECT_BILLING_ADDRESS.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_DEFAULT_BILLING_ADDRESS_COUNTRY.equals(path)) {
            return validateCountryCode(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_GO_LIVE_STAGE.equals(path)) {
            return validateCurrentGoLiveStageValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CURRENT_PSP_TEST_ACCOUNT_STAGE.equals(path)) {
            return validateCurrentPspTestAccountStageValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_SECTOR.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_SECTOR_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_INTERNAL.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_ARCHIVED.equals(path)) {
            return validateMandatoryBooleanValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_WENT_LIVE_DATE.equals(path)) {
            return validateZonedDateTimeValue(operation);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_NAME.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_NAME_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_1_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2.equals(path)) {
            return validateStringValueWithMaxLength(operation, true, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_ADDRESS_LINE_2_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_CITY.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_ADDRESS_CITY_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_COUNRTY.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_ADDRESS_COUNTRY_CODE_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE.equals(path)) {
            return validateStringValueWithMaxLength(operation, false, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_ADDRESS_POSTCODE_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_EMAIL.equals(path)) {
            return validateStringValueWithMaxLength(operation, true, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_EMAIL_MAX_LENGTH);
        } else if (uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER.equals(path)) {
            return validateStringValueWithMaxLength(operation, true, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.FIELD_MERCHANT_DETAILS_TELEPHONE_NUMBER_MAX_LENGTH);
        }
        return java.util.Collections.emptyList();
    }

    private java.util.List<java.lang.String> validateCustomBrandingValue(com.fasterxml.jackson.databind.JsonNode operation) {
        return requestValidations.checkExists(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).orElseGet(() -> uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.checkIfValidJson(operation.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE), uk.gov.pay.adminusers.service.ServiceUpdater.FIELD_CUSTOM_BRANDING));
    }

    private java.util.List<java.lang.String> validateServiceNameValue(com.fasterxml.jackson.databind.JsonNode operation, java.lang.String path) {
        boolean allowEmpty = !path.endsWith('/' + uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString());
        return validateStringValueWithMaxLength(operation, allowEmpty, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.SERVICE_NAME_MAX_LENGTH);
    }

    private java.util.List<java.lang.String> validateMandatoryBooleanValue(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        requestValidations.checkExists(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        if (errors.isEmpty()) {
            requestValidations.checkIsStrictBoolean(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }

    private java.util.List<java.lang.String> validateCurrentGoLiveStageValue(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        requestValidations.checkExistsAndNotEmpty(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        if (errors.isEmpty()) {
            requestValidations.checkIsString(java.lang.String.format("Field [%s] must be one of %s", uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.GO_LIVE_STAGES), operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        if (errors.isEmpty()) {
            requestValidations.isValidEnumValue(operation, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.GO_LIVE_STAGES, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }

    private java.util.List<java.lang.String> validateCurrentPspTestAccountStageValue(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        requestValidations.checkExistsAndNotEmpty(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        if (errors.isEmpty()) {
            requestValidations.checkIsString(java.lang.String.format("Field [%s] must be one of %s", uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.PSP_TEST_ACCOUNT_STAGES), operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        if (errors.isEmpty()) {
            requestValidations.isValidEnumValue(operation, uk.gov.pay.adminusers.resources.ServiceUpdateOperationValidator.PSP_TEST_ACCOUNT_STAGES, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }

    private java.util.List<java.lang.String> validateStringValueWithMaxLength(com.fasterxml.jackson.databind.JsonNode operation, boolean allowEmpty, int maxLength) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        if (allowEmpty) {
            requestValidations.checkExists(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        } else {
            requestValidations.checkExistsAndNotEmpty(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        if (errors.isEmpty()) {
            requestValidations.checkIsString(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        if (errors.isEmpty()) {
            requestValidations.checkMaxLength(operation, maxLength, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }

    private java.util.List<java.lang.String> validateOperationIsValidForPath(com.fasterxml.jackson.databind.JsonNode operation) {
        java.lang.String path = operation.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_PATH).asText();
        if (!validAttributeUpdateOperations.containsKey(path)) {
            return java.util.Collections.singletonList(java.lang.String.format("Path [%s] is invalid", path));
        }
        java.lang.String op = operation.get("op").asText();
        if (!validAttributeUpdateOperations.get(path).contains(op)) {
            return java.util.Collections.singletonList(java.lang.String.format("Operation [%s] is invalid for path [%s]", op, path));
        }
        return java.util.Collections.emptyList();
    }

    private static java.util.List<java.lang.String> checkIfValidJson(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String fieldName) {
        if ((payload == null) || (!payload.isObject())) {
            return java.util.Collections.singletonList(java.lang.String.format("Value for path [%s] must be a JSON", fieldName));
        }
        return java.util.Collections.emptyList();
    }

    private java.util.List<java.lang.String> validateZonedDateTimeValue(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        requestValidations.checkExists(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        if (errors.isEmpty()) {
            requestValidations.checkIsZonedDateTime(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }

    private java.util.List<java.lang.String> validateCountryCode(com.fasterxml.jackson.databind.JsonNode operation) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        if (operation.get(uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).isNull()) {
            return errors;
        }
        requestValidations.checkIsString(operation, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        if (errors.isEmpty()) {
            requestValidations.checkMaxLength(operation, 2, uk.gov.pay.adminusers.model.ServiceUpdateRequest.FIELD_VALUE).ifPresent(errors::addAll);
        }
        return errors;
    }
}
