package uk.gov.pay.adminusers.pact;
@org.junit.runner.RunWith(au.com.dius.pact.provider.junit.PactRunner.class)
@au.com.dius.pact.provider.junit.Provider("adminusers")
@au.com.dius.pact.provider.junit.loader.PactBroker(scheme = "https", host = "${PACT_BROKER_HOST:pact-broker-test.cloudapps.digital}", tags = { "${PACT_CONSUMER_TAG}", "test-fargate" }, authentication = @au.com.dius.pact.provider.junit.loader.PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"), consumers = { "selfservice" })
public class SelfServiceContractTest extends uk.gov.pay.adminusers.pact.ContractTest {}
