package uk.gov.pay.adminusers.resources;
public class StripeAgreementRequestTest {
    private javax.validation.Validator validator;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        javax.validation.ValidatorFactory factory = javax.validation.Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @org.junit.jupiter.api.Test
    public void shouldPassValidIPv4Address() {
        uk.gov.pay.adminusers.model.StripeAgreementRequest stripeAgreementRequest = new uk.gov.pay.adminusers.model.StripeAgreementRequest("192.0.2.0");
        org.hamcrest.MatcherAssert.assertThat(validator.validate(stripeAgreementRequest).isEmpty(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldPassValidIPv6Address() {
        uk.gov.pay.adminusers.model.StripeAgreementRequest stripeAgreementRequest = new uk.gov.pay.adminusers.model.StripeAgreementRequest("2001:DB8:0000:0000:0000:0000:0000:0000");
        org.hamcrest.MatcherAssert.assertThat(validator.validate(stripeAgreementRequest).isEmpty(), org.hamcrest.core.Is.is(true));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorForInvalidIPAddress() {
        uk.gov.pay.adminusers.model.StripeAgreementRequest stripeAgreementRequest = new uk.gov.pay.adminusers.model.StripeAgreementRequest("257.0.2.0");
        java.util.Set<javax.validation.ConstraintViolation<uk.gov.pay.adminusers.model.StripeAgreementRequest>> violations = validator.validate(stripeAgreementRequest);
        org.hamcrest.MatcherAssert.assertThat(violations.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(violations.iterator().next().getMessage(), org.hamcrest.core.Is.is("must be valid IP address"));
    }

    @org.junit.jupiter.api.Test
    public void shouldErrorWhenIpAddressIsEmpty() {
        uk.gov.pay.adminusers.model.StripeAgreementRequest stripeAgreementRequest = new uk.gov.pay.adminusers.model.StripeAgreementRequest("");
        java.util.Set<javax.validation.ConstraintViolation<uk.gov.pay.adminusers.model.StripeAgreementRequest>> violations = validator.validate(stripeAgreementRequest);
        org.hamcrest.MatcherAssert.assertThat(violations.size(), org.hamcrest.core.Is.is(1));
        org.hamcrest.MatcherAssert.assertThat(violations.iterator().next().getMessage(), org.hamcrest.core.Is.is("must be valid IP address"));
    }
}
