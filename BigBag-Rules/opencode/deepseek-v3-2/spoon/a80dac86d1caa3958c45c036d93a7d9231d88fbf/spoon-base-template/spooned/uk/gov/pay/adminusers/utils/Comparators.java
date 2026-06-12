package uk.gov.pay.adminusers.utils;
public class Comparators {
    public static java.util.Comparator<java.lang.String> usingNumericComparator() {
        return java.util.Comparator.comparingLong(java.lang.Long::valueOf);
    }

    public static java.util.Comparator<java.lang.String> numericallyThenLexicographically() {
        return (o1, o2) -> {
            if (org.apache.commons.lang3.math.NumberUtils.isDigits(o1) && org.apache.commons.lang3.math.NumberUtils.isDigits(o2)) {
                return uk.gov.pay.adminusers.utils.Comparators.usingNumericComparator().compare(o1, o2);
            } else if (org.apache.commons.lang3.math.NumberUtils.isDigits(o1)) {
                return -1;
            } else if (org.apache.commons.lang3.math.NumberUtils.isDigits(o2)) {
                return 1;
            } else {
                return o1.compareTo(o2);
            }
        };
    }
}
