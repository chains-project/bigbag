package uk.gov.pay.adminusers.infra;
public class NotifyStub {
    private final com.github.tomakehurst.wiremock.WireMockServer wireMockServer;

    public NotifyStub(com.github.tomakehurst.wiremock.WireMockServer wireMockServer) {
        this.wireMockServer = wireMockServer;
    }

    public void stubSendEmail() {
        java.lang.String responseBody = new com.google.gson.GsonBuilder().create().toJson(java.util.Map.of("id", "d2c1a8d1-b897-4013-b761-f2b442ebdc10", "reference", "a-reference", "content", java.util.Map.of("body", "body", "subject", "subject"), "template", java.util.Map.of("id", "1d2ce804-b51c-4108-a306-1789bd2699e0", "version", 1, "uri", "template-uri")));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/v2/notifications/email")).willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(201).withBody(responseBody)));
    }
}
