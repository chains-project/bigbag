package uk.gov.pay.adminusers.utils;
public class ComparatorsTest {
    @org.junit.jupiter.api.Test
    public void shouldOrderNumericStringsInAscendingOrder() {
        java.util.List<java.lang.String> result = java.util.stream.Stream.of("1", "6", "4", "10", "5").sorted(uk.gov.pay.adminusers.utils.Comparators.usingNumericComparator()).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(result, org.hamcrest.CoreMatchers.is(java.util.Arrays.asList("1", "4", "5", "6", "10")));
    }

    @org.junit.jupiter.api.Test
    public void shouldOrderGatewayAccountsIdsNumericallyThenLexicographically() {
        java.util.List<java.lang.String> result = java.util.stream.Stream.of("1aaa", "1", "6", "cde", "4", "bbb23", "10", "5").sorted(uk.gov.pay.adminusers.utils.Comparators.numericallyThenLexicographically()).collect(java.util.stream.Collectors.toUnmodifiableList());
        org.hamcrest.MatcherAssert.assertThat(result, org.hamcrest.CoreMatchers.is(java.util.Arrays.asList("1", "4", "5", "6", "10", "1aaa", "bbb23", "cde")));
    }
}
