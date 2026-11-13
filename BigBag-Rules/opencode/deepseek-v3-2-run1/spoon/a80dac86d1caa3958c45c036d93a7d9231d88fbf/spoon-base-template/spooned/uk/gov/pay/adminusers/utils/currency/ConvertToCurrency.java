package uk.gov.pay.adminusers.utils.currency;
public class ConvertToCurrency {
    public static java.util.function.Function<java.lang.Long, java.math.BigDecimal> convertPenceToPounds = pence -> new java.math.BigDecimal(pence).movePointLeft(2);
}
