package uk.gov.pay.adminusers.validations;
public class UserPatchValidations {
    private static final java.util.List<java.lang.String> PATCH_ALLOWED_PATHS = java.util.List.of(uk.gov.pay.adminusers.model.PatchRequest.PATH_SESSION_VERSION, uk.gov.pay.adminusers.model.PatchRequest.PATH_DISABLED, uk.gov.pay.adminusers.model.PatchRequest.PATH_TELEPHONE_NUMBER, uk.gov.pay.adminusers.model.PatchRequest.PATH_FEATURES, uk.gov.pay.adminusers.model.PatchRequest.PATH_EMAIL);

    private static final java.util.Map<java.lang.String, java.lang.String> USER_PATCH_PATH_OPS = java.util.Map.of(uk.gov.pay.adminusers.model.PatchRequest.PATH_SESSION_VERSION, "append", uk.gov.pay.adminusers.model.PatchRequest.PATH_DISABLED, "replace", uk.gov.pay.adminusers.model.PatchRequest.PATH_TELEPHONE_NUMBER, "replace", uk.gov.pay.adminusers.model.PatchRequest.PATH_EMAIL, "replace", uk.gov.pay.adminusers.model.PatchRequest.PATH_FEATURES, "replace");

    private static final com.google.common.collect.Multimap<java.lang.String, org.apache.commons.lang3.tuple.Pair<java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean>, java.lang.String>> USER_PATCH_PATH_VALIDATIONS = new com.google.common.collect.ImmutableListMultimap.Builder<java.lang.String, org.apache.commons.lang3.tuple.Pair<java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean>, java.lang.String>>().put(uk.gov.pay.adminusers.model.PatchRequest.PATH_SESSION_VERSION, org.apache.commons.lang3.tuple.Pair.of(uk.gov.pay.adminusers.validations.RequestValidations.isNotNumeric(), java.lang.String.format("path [%s] must contain a value of positive integer", uk.gov.pay.adminusers.model.PatchRequest.PATH_SESSION_VERSION))).put(uk.gov.pay.adminusers.model.PatchRequest.PATH_DISABLED, org.apache.commons.lang3.tuple.Pair.of(uk.gov.pay.adminusers.validations.RequestValidations.isNotBoolean(), java.lang.String.format("path [%s] must be contain value [true | false]", uk.gov.pay.adminusers.model.PatchRequest.PATH_DISABLED))).put(uk.gov.pay.adminusers.model.PatchRequest.PATH_TELEPHONE_NUMBER, org.apache.commons.lang3.tuple.Pair.of(uk.gov.pay.adminusers.validations.RequestValidations.isNotValidTelephoneNumber(), java.lang.String.format("path [%s] must contain a valid telephone number", uk.gov.pay.adminusers.model.PatchRequest.PATH_TELEPHONE_NUMBER))).put(uk.gov.pay.adminusers.model.PatchRequest.PATH_EMAIL, org.apache.commons.lang3.tuple.Pair.of(uk.gov.pay.adminusers.validations.RequestValidations.isNotValidEmail(), java.lang.String.format("path [%s] must contain a valid email", uk.gov.pay.adminusers.model.PatchRequest.PATH_EMAIL))).build();

    public static boolean isPathAllowed(java.lang.String path) {
        return uk.gov.pay.adminusers.validations.UserPatchValidations.PATCH_ALLOWED_PATHS.contains(path);
    }

    public static boolean isAllowedOpForPath(java.lang.String path, java.lang.String op) {
        return uk.gov.pay.adminusers.validations.UserPatchValidations.USER_PATCH_PATH_OPS.get(path).contains(op);
    }

    public static java.util.Collection<org.apache.commons.lang3.tuple.Pair<java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.lang.Boolean>, java.lang.String>> getUserPatchPathValidations(java.lang.String path) {
        return uk.gov.pay.adminusers.validations.UserPatchValidations.USER_PATCH_PATH_VALIDATIONS.get(path);
    }
}
