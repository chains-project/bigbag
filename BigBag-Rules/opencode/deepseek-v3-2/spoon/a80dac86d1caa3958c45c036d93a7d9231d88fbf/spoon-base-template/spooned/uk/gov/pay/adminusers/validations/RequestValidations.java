package uk.gov.pay.adminusers.validations;
public class RequestValidations {
    public java.util.Optional<java.util.List<java.lang.String>> checkIsNumeric(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotNumeric(), fieldNames, "Field [%s] must be a number");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsBoolean(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotBoolean(), fieldNames, "Field [%s] must be a boolean");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsStrictBoolean(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotStrictBoolean(), fieldNames, "Field [%s] must be a boolean");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkExistsAndNotEmpty(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, notExistsOrIsEmpty(), fieldNames, "Field [%s] is required");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkExists(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, notExists(), fieldNames, "Field [%s] is required");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkMaxLength(com.fasterxml.jackson.databind.JsonNode payload, int maxLength, java.lang.String... fieldNames) {
        return applyCheck(payload, exceedsMaxLength(maxLength), fieldNames, ("Field [%s] must have a maximum length of " + maxLength) + " characters");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsString(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotString(), fieldNames, "Field [%s] must be a string");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsString(java.lang.String errorMsg, com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotString(), fieldNames, errorMsg);
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsValidTelephoneNumber(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotValidTelephoneNumber(), fieldNames, "Field [%s] must be a valid telephone number");
    }

    public java.util.Optional<java.util.List<java.lang.String>> checkIsZonedDateTime(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, uk.gov.pay.adminusers.validations.RequestValidations.isNotZonedDateTime(), fieldNames, "Field [%s] must be a valid date time with timezone");
    }

    private java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> exceedsMaxLength(int maxLength) {
        return jsonNode -> jsonNode.asText().length() > maxLength;
    }

    public java.util.Optional<java.util.List<java.lang.String>> applyCheck(com.fasterxml.jackson.databind.JsonNode payload, java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> check, java.lang.String[] fieldNames, java.lang.String errorMessage) {
        java.util.List<java.lang.String> errors = new java.util.ArrayList<>();
        for (java.lang.String fieldName : fieldNames) {
            if (check.apply(payload.get(fieldName))) {
                errors.add(java.lang.String.format(errorMessage, fieldName));
            }
        }
        return errors.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(errors);
    }

    public java.util.Optional<java.util.List<java.lang.String>> isValidEnumValue(com.fasterxml.jackson.databind.JsonNode payload, java.util.EnumSet<?> enumSet, java.lang.String field) {
        java.lang.String value = payload.get(field).asText();
        if (enumSet.stream().noneMatch(constant -> constant.name().equals(value))) {
            return java.util.Optional.of(java.util.Collections.singletonList(java.lang.String.format("Field [%s] must be one of %s", field, enumSet)));
        }
        return java.util.Optional.empty();
    }

    private java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> notExistsOrIsEmpty() {
        return (com.fasterxml.jackson.databind.JsonNode jsonElement) -> {
            if (jsonElement instanceof com.fasterxml.jackson.databind.node.NullNode) {
                return uk.gov.pay.adminusers.validations.RequestValidations.isNullValue().apply(jsonElement);
            } else if (jsonElement instanceof com.fasterxml.jackson.databind.node.ArrayNode) {
                return notExistOrEmptyArray().apply(jsonElement);
            } else {
                return uk.gov.pay.adminusers.validations.RequestValidations.notExistOrBlankText().apply(jsonElement);
            }
        };
    }

    private java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> notExists() {
        return (com.fasterxml.jackson.databind.JsonNode jsonElement) -> uk.gov.pay.adminusers.validations.RequestValidations.isNullValue().apply(jsonElement);
    }

    private java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> notExistOrEmptyArray() {
        return jsonElement -> (jsonElement == null) || ((jsonElement instanceof com.fasterxml.jackson.databind.node.ArrayNode) && (jsonElement.size() == 0));
    }

    private static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> notExistOrBlankText() {
        return jsonElement -> (jsonElement == null) || org.apache.commons.lang3.StringUtils.isBlank(jsonElement.asText());
    }

    private static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNullValue() {
        return jsonElement -> (jsonElement == null) || (jsonElement instanceof com.fasterxml.jackson.databind.node.NullNode);
    }

    static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotNumeric() {
        return jsonNode -> !org.apache.commons.lang3.math.NumberUtils.isDigits(jsonNode.asText());
    }

    static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotBoolean() {
        return jsonNode -> !java.util.List.of("true", "false").contains(jsonNode.asText().toLowerCase(java.util.Locale.ENGLISH));
    }

    private static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotStrictBoolean() {
        return jsonNode -> !jsonNode.isBoolean();
    }

    private static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotString() {
        return jsonNode -> !jsonNode.isTextual();
    }

    static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotValidTelephoneNumber() {
        return jsonNode -> !uk.gov.pay.adminusers.utils.telephonenumber.TelephoneNumberUtility.isValidPhoneNumber(jsonNode.asText());
    }

    static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotValidEmail() {
        return jsonNode -> !uk.gov.pay.adminusers.utils.email.EmailValidator.isValid(jsonNode.asText());
    }

    private static java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean> isNotZonedDateTime() {
        return jsonNode -> (!jsonNode.isTextual()) || (!uk.gov.pay.adminusers.validations.RequestValidations.isZonedDateTime(jsonNode.textValue()));
    }

    private static boolean isZonedDateTime(java.lang.String value) {
        try {
            java.time.ZonedDateTime.parse(value);
            return true;
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    public java.util.Optional<java.util.List<java.lang.String>> isValidEmail(com.fasterxml.jackson.databind.JsonNode payload, java.lang.String... fieldNames) {
        return applyCheck(payload, jsonNode -> !uk.gov.pay.adminusers.utils.email.EmailValidator.isValid(jsonNode.asText()), fieldNames, "Field [email] must be a valid email address");
    }
}
