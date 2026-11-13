package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ForgottenPassword {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.lang.Integer id;

    private java.lang.String code;

    private java.time.ZonedDateTime date;

    private java.lang.String userExternalId;

    private java.util.List<uk.gov.pay.adminusers.model.Link> links;

    public static uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword(java.lang.String code, java.lang.String userExternalId) {
        return uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), code, java.time.ZonedDateTime.now(java.time.ZoneId.of("UTC")), userExternalId);
    }

    public static uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword(java.lang.Integer id, java.lang.String code, java.time.ZonedDateTime date, java.lang.String userExternalId) {
        return new uk.gov.pay.adminusers.model.ForgottenPassword(id, code, date, userExternalId);
    }

    private ForgottenPassword(java.lang.Integer id, java.lang.String code, java.time.ZonedDateTime date, java.lang.String userExternalId) {
        this.id = id;
        this.code = code;
        this.date = date;
        this.userExternalId = userExternalId;
    }

    public java.lang.Integer getId() {
        return id;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "bc9039e00cba4e63b2c92ecd0e188aba")
    public java.lang.String getCode() {
        return code;
    }

    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = uk.gov.service.payments.commons.api.json.ApiResponseDateTimeSerializer.class)
    @io.swagger.v3.oas.annotations.media.Schema(example = "2022-04-06T21:27:06.376Z")
    public java.time.ZonedDateTime getDate() {
        return date;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "12e3eccfab284ae5bc1108e9c0456ba7")
    public java.lang.String getUserExternalId() {
        return userExternalId;
    }

    public void setLinks(java.util.List<uk.gov.pay.adminusers.model.Link> links) {
        this.links = links;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("_links")
    public java.util.List<uk.gov.pay.adminusers.model.Link> getLinks() {
        return links;
    }
}
