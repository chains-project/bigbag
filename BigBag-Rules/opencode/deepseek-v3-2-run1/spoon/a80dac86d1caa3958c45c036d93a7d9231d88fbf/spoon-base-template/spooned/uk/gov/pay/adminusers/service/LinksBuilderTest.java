package uk.gov.pay.adminusers.service;
class LinksBuilderTest {
    private static com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private uk.gov.pay.adminusers.service.LinksBuilder linksBuilder = new uk.gov.pay.adminusers.service.LinksBuilder("http://localhost:8080");

    @org.junit.jupiter.api.Test
    void shouldConstruct_userSelfLinkCorrectly() throws java.lang.Exception {
        uk.gov.pay.adminusers.model.Service service = uk.gov.pay.adminusers.model.Service.from(2, "34783g87ebg764r", new uk.gov.pay.adminusers.model.ServiceName(uk.gov.pay.adminusers.model.Service.DEFAULT_NAME_VALUE));
        uk.gov.pay.adminusers.model.Role role = uk.gov.pay.adminusers.model.Role.role(2, "blah", "blah");
        uk.gov.pay.adminusers.model.ServiceRole serviceRole = uk.gov.pay.adminusers.model.ServiceRole.from(service, role);
        uk.gov.pay.adminusers.model.User user = uk.gov.pay.adminusers.model.User.from(uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomInt(), uk.gov.pay.adminusers.app.util.RandomIdGenerator.randomUuid(), "a-username", "a-password", "email@example.com", "4wrwef", "123435", java.util.Collections.singletonList(serviceRole), null, uk.gov.pay.adminusers.model.SecondFactorMethod.SMS, null, null, null);
        uk.gov.pay.adminusers.model.User decoratedUser = linksBuilder.decorate(user);
        java.lang.String linkJson = uk.gov.pay.adminusers.service.LinksBuilderTest.objectMapper.writeValueAsString(decoratedUser.getLinks().get(0));
        org.hamcrest.MatcherAssert.assertThat(linkJson, org.hamcrest.core.Is.is(("{\"rel\":\"self\",\"method\":\"GET\",\"href\":\"http://localhost:8080/v1/api/users/" + decoratedUser.getExternalId()) + "\"}"));
    }

    @org.junit.jupiter.api.Test
    void shouldConstruct_forgottenPasswordSelfLinkCorrectly() throws java.lang.Exception {
        uk.gov.pay.adminusers.model.ForgottenPassword forgottenPassword = uk.gov.pay.adminusers.model.ForgottenPassword.forgottenPassword(1, "a-code", java.time.ZonedDateTime.now(), "7d19aff33f8948deb97ed16b2912dcd3");
        uk.gov.pay.adminusers.model.ForgottenPassword decorated = linksBuilder.decorate(forgottenPassword);
        java.lang.String linkJson = uk.gov.pay.adminusers.service.LinksBuilderTest.objectMapper.writeValueAsString(decorated.getLinks().get(0));
        org.hamcrest.MatcherAssert.assertThat(linkJson, org.hamcrest.core.Is.is("{\"rel\":\"self\",\"method\":\"GET\",\"href\":\"http://localhost:8080/v1/api/forgotten-passwords/a-code\"}"));
    }
}
