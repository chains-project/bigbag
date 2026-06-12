package uk.gov.pay.adminusers.model;
@com.fasterxml.jackson.databind.annotation.JsonNaming(com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy.class)
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
public class Link {
    public enum Rel {

        SELF,
        INVITE,
        USER;
    }

    private final uk.gov.pay.adminusers.model.Link.Rel rel;

    private final java.lang.String method;

    private final java.lang.String href;

    public static uk.gov.pay.adminusers.model.Link from(uk.gov.pay.adminusers.model.Link.Rel rel, java.lang.String method, java.lang.String href) {
        return new uk.gov.pay.adminusers.model.Link(rel, method, href);
    }

    private Link(uk.gov.pay.adminusers.model.Link.Rel rel, java.lang.String method, java.lang.String href) {
        this.rel = rel;
        this.method = method;
        this.href = href;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("rel")
    @io.swagger.v3.oas.annotations.media.Schema(example = "self")
    public java.lang.String getRelAsLowerCase() {
        return rel.name().toLowerCase(java.util.Locale.ENGLISH);
    }

    public uk.gov.pay.adminusers.model.Link.Rel getRel() {
        return rel;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "GET")
    public java.lang.String getMethod() {
        return method;
    }

    @io.swagger.v3.oas.annotations.media.Schema(example = "https://an.example.link")
    public java.lang.String getHref() {
        return href;
    }

    @java.lang.Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        uk.gov.pay.adminusers.model.Link link = ((uk.gov.pay.adminusers.model.Link) (o));
        return (java.util.Objects.equals(rel, link.rel) && java.util.Objects.equals(method, link.method)) && java.util.Objects.equals(href, link.href);
    }

    @java.lang.Override
    public int hashCode() {
        return java.util.Objects.hash(rel, method, href);
    }

    @java.lang.Override
    public java.lang.String toString() {
        return java.lang.String.format("Link{rel=%s, method='%s', href='%s'}", getRelAsLowerCase(), method, href);
    }
}
