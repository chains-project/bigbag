package uk.gov.pay.adminusers.unit.service;
abstract class ServiceResourceBaseTest {
    /* default */
    static final java.lang.String GATEWAY_ACCOUNT_ID = "some-gateway-account-id";

    /* default */
    static final java.lang.String CY_SERVICE_NAME = "some-welsh-service-name";

    /* default */
    static final java.lang.String EN_SERVICE_NAME = "some-test-service-name";

    /* default */
    static uk.gov.pay.adminusers.persistence.dao.ServiceDao mockedServiceDao = org.mockito.Mockito.mock(uk.gov.pay.adminusers.persistence.dao.ServiceDao.class);

    private static final java.lang.String HTTPS_BASE_URL = "https://base-url";

    /* default */
    static final uk.gov.pay.adminusers.service.LinksBuilder LINKS_BUILDER = new uk.gov.pay.adminusers.service.LinksBuilder(uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.HTTPS_BASE_URL);

    /* default */
    static void assertLinks(java.lang.String serviceExternalId, io.restassured.path.json.JsonPath json) {
        org.hamcrest.MatcherAssert.assertThat(json.getList("_links"), org.hamcrest.Matchers.hasSize(1));
        org.hamcrest.MatcherAssert.assertThat(json.get("_links[0].href"), org.hamcrest.core.Is.is((uk.gov.pay.adminusers.unit.service.ServiceResourceBaseTest.HTTPS_BASE_URL + "/v1/api/services/") + serviceExternalId));
        org.hamcrest.MatcherAssert.assertThat(json.get("_links[0].method"), org.hamcrest.core.Is.is("GET"));
        org.hamcrest.MatcherAssert.assertThat(json.get("_links[0].rel"), org.hamcrest.core.Is.is("self"));
    }

    /* default */
    static void assertEnServiceNameJson(java.lang.String name, io.restassured.path.json.JsonPath json) {
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.ENGLISH.toString()));
        org.hamcrest.MatcherAssert.assertThat(json.get("service_name.en"), org.hamcrest.core.Is.is(name));
    }

    /* default */
    static void assertCyServiceNameJson(java.lang.String cyName, io.restassured.path.json.JsonPath json) {
        org.hamcrest.MatcherAssert.assertThat(json.getMap("service_name"), org.hamcrest.Matchers.hasKey(uk.gov.service.payments.commons.model.SupportedLanguage.WELSH.toString()));
        org.hamcrest.MatcherAssert.assertThat(json.get("service_name.cy"), org.hamcrest.core.Is.is(cyName));
    }
}
