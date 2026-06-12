package uk.gov.pay.adminusers.pact;
@org.junit.runner.RunWith(org.junit.runners.AllTests.class)
public class ProviderContractTestSuite {
    public static junit.framework.TestSuite suite() {
        com.google.common.collect.ImmutableSetMultimap<java.lang.String, junit.framework.JUnit4TestAdapter> map = com.google.common.collect.ImmutableSetMultimap.of("products-ui", new junit.framework.JUnit4TestAdapter(uk.gov.pay.adminusers.pact.ProductsUIContractTest.class), "selfservice", new junit.framework.JUnit4TestAdapter(uk.gov.pay.adminusers.pact.SelfServiceContractTest.class));
        return uk.gov.service.payments.commons.testing.pact.provider.CreateTestSuite.create(map);
    }
}
