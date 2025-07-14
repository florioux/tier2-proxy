package eu.europa.ec.simpl.tier2proxy.certificate;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;

@UtilityClass
public class DNBuilder {

    private static final int KEY_VALUE_DN_ARRAY_LENGTH = 2;

    private static final Map<String, ASN1ObjectIdentifier> DN_OID_MAP = Map.ofEntries(
            Map.entry("C", BCStyle.C),
            Map.entry("ST", BCStyle.ST),
            Map.entry("L", BCStyle.L),
            Map.entry("O", BCStyle.O),
            Map.entry("OU", BCStyle.OU),
            Map.entry("CN", BCStyle.CN),
            Map.entry("EMAILADDRESS", BCStyle.EmailAddress),
            Map.entry("SERIALNUMBER", BCStyle.SERIALNUMBER),
            Map.entry("STREET", BCStyle.STREET),
            Map.entry("DC", BCStyle.DC),
            Map.entry("UID", BCStyle.UID),
            Map.entry("T", BCStyle.T),
            Map.entry("DNQUALIFIER", BCStyle.DN_QUALIFIER),
            Map.entry("GIVENNAME", BCStyle.GIVENNAME),
            Map.entry("INITIALS", BCStyle.INITIALS),
            Map.entry("GENERATION", BCStyle.GENERATION),
            Map.entry("SURNAME", BCStyle.SURNAME),
            Map.entry("PSEUDONYM", BCStyle.PSEUDONYM),
            Map.entry("POSTALCODE", BCStyle.POSTAL_CODE),
            Map.entry("BUSINESSCATEGORY", BCStyle.BUSINESS_CATEGORY),
            Map.entry("TELEPHONENUMBER", BCStyle.TELEPHONE_NUMBER),
            Map.entry("NAME", BCStyle.NAME),
            Map.entry("E", BCStyle.EmailAddress),
            Map.entry("OIDDN", BCStyle.DN_QUALIFIER));

    public static X500Name buildDN(String x500String) {
        var dn = new X500NameBuilder(BCStyle.INSTANCE);

        Arrays.stream(x500String.split(","))
                .map(String::trim)
                .map(part -> part.split("="))
                .filter(entry -> entry.length == KEY_VALUE_DN_ARRAY_LENGTH)
                .forEach(entry -> {
                    var key = entry[0].trim().toUpperCase(Locale.getDefault());
                    var value = entry[1].trim();
                    var oid = DN_OID_MAP.get(key);
                    if (oid != null) {
                        dn.addRDN(oid, value);
                    } else {
                        throw new IllegalArgumentException("Unsupported DN key: " + key);
                    }
                });

        return dn.build();
    }
}
