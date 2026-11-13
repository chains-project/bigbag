package uk.gov.pay.adminusers.utils.email;
public class EmailValidator {
    /**
     * {@link #PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS_IN_ASCENDING_ORDER} is based on:<br>
     * - <a href="https://en.wikipedia.org/wiki/.uk">en.wikipedia.org/wiki/.uk</a><br>
     * - <a href="https://github.com/alphagov/notifications-admin/blob/9391181b2c7d077ea8fe0a72c718ab8f7fdbcd0c/app/config.py#L67">alphagov/notifications-admin</a><br>
     */
    private static final java.util.List<java.lang.String> PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS_IN_ASCENDING_ORDER = java.util.List.of("acas.org.uk", "accessplanit.com", "achievingforchildren.org.uk", "assembly.wales", "beechpc.com", "bl.uk", "caa.co.uk", "careinspectorate.com", "cynulliad.cymru", "derrystrabane.com", "digitalaccessibilitycentre.org", "eani.org.uk", "fermanaghomagh.com", "forestryengland.uk", "gov.scot", "gov.uk", "gov.wales", "hial.co.uk", "hmcts.net", "judiciary.uk", "llyw.cymru", "mil.uk", "mod.uk", "naturalengland.org.uk", "nature.scot", "nexus.org.uk", "nhm.ac.uk", "nhs.net", "nhs.scot", "nhs.uk", "nls.uk", "nmandd.org", "nmni.com", "ogauthority.co.uk", "os.uk", "parliament.scot", "parliament.uk", "police.uk", "prrt.org", "scotent.co.uk", "serc.ac.uk", "slc.co.uk", "socialworkengland.org.uk", "sssc.uk.com", "tfgm.com", "ucds.email", "uksbs.co.uk", "wmca.org.uk", "york.ac.uk");

    private static final java.util.regex.Pattern PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN;

    static {
        java.lang.String domainRegExPatternString = PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERNS_IN_ASCENDING_ORDER.stream().map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        // We are splitting the logic into two parts for whitelisted domains and subdomains
        java.lang.String regExDomainsOnlyPart = ("(" + domainRegExPatternString) + ")";
        java.lang.String regExSubdomainsPart = ("(((?!-)[A-Za-z0-9-]+(?<!-)\\.)+(" + domainRegExPatternString) + "))";
        PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN = java.util.regex.Pattern.compile(((("^" + regExDomainsOnlyPart) + "|") + regExSubdomainsPart) + "$");
    }

    private static final org.apache.commons.validator.routines.EmailValidator COMMONS_EMAIL_VALIDATOR = org.apache.commons.validator.routines.EmailValidator.getInstance();

    public static boolean isValid(java.lang.String email) {
        return uk.gov.pay.adminusers.utils.email.EmailValidator.COMMONS_EMAIL_VALIDATOR.isValid(email);
    }

    public static boolean isPublicSectorEmail(java.lang.String email) {
        java.lang.String lowerCaseEmail = email.toLowerCase(java.util.Locale.ENGLISH);
        java.lang.String[] emailParts = lowerCaseEmail.split("@");
        if (((emailParts.length != 2) || emailParts[0].isEmpty()) || emailParts[1].isEmpty()) {
            return false;
        }
        java.lang.String domain = emailParts[1];
        java.util.regex.Matcher matcher = uk.gov.pay.adminusers.utils.email.EmailValidator.PUBLIC_SECTOR_EMAIL_DOMAIN_REGEX_PATTERN.matcher(domain);
        return matcher.matches();
    }
}
