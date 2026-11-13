package uk.gov.pay.adminusers.utils;
public class Errors {
    private java.util.List<java.lang.String> errors;

    private Errors(@com.fasterxml.jackson.annotation.JsonProperty("errors")
    java.util.List<java.lang.String> errors) {
        this.errors = errors;
    }

    public static uk.gov.pay.adminusers.utils.Errors from(java.lang.String error) {
        return new uk.gov.pay.adminusers.utils.Errors(java.util.Collections.singletonList(error));
    }

    public static uk.gov.pay.adminusers.utils.Errors from(java.util.List<java.lang.String> errorList) {
        return new uk.gov.pay.adminusers.utils.Errors(errorList);
    }

    @com.fasterxml.jackson.annotation.JsonGetter
    public java.util.List<java.lang.String> getErrors() {
        return errors;
    }

    public void setErrors(java.util.List<java.lang.String> errors) {
        this.errors = errors;
    }
}
