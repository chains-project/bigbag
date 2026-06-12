package uk.gov.pay.adminusers.app.util;
public class RandomIdGenerator {
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private static final java.util.Random RANDOM = new java.util.Random();

    /**
     * This method will generate a URL safe random string.
     * This works by choosing 130 bits from a cryptographically secure random bit generator,
     * and encoding them in base-32.
     * <p> 128 bits is considered to be cryptographically strong,
     * but each digit in a base 32 number can encode 5 bits, so 128 is rounded up to the next multiple of 5.
     * This encoding is compact and efficient, with 5 random bits per character. Compare this to a random UUID,
     * which only has 3.4 bits per character in standard layout, and only 122 random bits in total </p>
     *
     * @return a random number in base32 (in string format)
     */
    public static java.lang.String newId() {
        return new java.math.BigInteger(130, uk.gov.pay.adminusers.app.util.RandomIdGenerator.SECURE_RANDOM).toString(32);
    }

    public static java.lang.Integer randomInt() {
        return uk.gov.pay.adminusers.app.util.RandomIdGenerator.RANDOM.nextInt(java.lang.Integer.MAX_VALUE);
    }

    public static java.lang.String randomUuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").toLowerCase(java.util.Locale.ENGLISH);
    }
}
