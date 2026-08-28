package com.bankflow.common.iban;

import java.util.Map;

/**
 * Checks that an IBAN could exist before a payment is built around it.
 *
 * An IBAN is self-checking by design: two of its characters are a checksum
 * over the rest, so a mistyped digit is detectable without asking anybody.
 * That is the entire reason the format exists, and not using it means a
 * payment leaves on an account number that was never real.
 *
 * The scheme will not catch this for us. ISO 20022 types IBAN as
 * {@code [A-Z]{2}[0-9]{2}[a-zA-Z0-9]{1,30}}, which any plausible-looking
 * string satisfies -- so a wrong-length Kosovo IBAN passes XSD validation,
 * goes to KIPS, and comes back days later as a rejection the customer has
 * been waiting on. Validating here turns that into an error at the keyboard.
 */
public final class Iban {

    /**
     * Length of a complete IBAN per country, from the SWIFT IBAN registry.
     *
     * Not every country in the world -- the ones this bank can actually reach.
     * An unknown country is not rejected outright, because being absent from
     * this list says something about the list, not the IBAN; it still has to
     * be structurally sound and pass its checksum.
     */
    private static final Map<String, Integer> LENGTHS = Map.ofEntries(
            Map.entry("XK", 20),
            Map.entry("AL", 28),
            Map.entry("AT", 20),
            Map.entry("BA", 20),
            Map.entry("BE", 16),
            Map.entry("BG", 22),
            Map.entry("CH", 21),
            Map.entry("CY", 28),
            Map.entry("CZ", 24),
            Map.entry("DE", 22),
            Map.entry("DK", 18),
            Map.entry("EE", 20),
            Map.entry("ES", 24),
            Map.entry("FI", 18),
            Map.entry("FR", 27),
            Map.entry("GB", 22),
            Map.entry("GR", 27),
            Map.entry("HR", 21),
            Map.entry("HU", 28),
            Map.entry("IE", 22),
            Map.entry("IT", 27),
            Map.entry("LT", 20),
            Map.entry("LU", 20),
            Map.entry("LV", 21),
            Map.entry("ME", 22),
            Map.entry("MK", 19),
            Map.entry("MT", 31),
            Map.entry("NL", 18),
            Map.entry("NO", 15),
            Map.entry("PL", 28),
            Map.entry("PT", 25),
            Map.entry("RO", 24),
            Map.entry("RS", 22),
            Map.entry("SE", 24),
            Map.entry("SI", 19),
            Map.entry("SK", 24),
            Map.entry("SM", 27),
            Map.entry("TR", 26)
    );

    /** The widest an IBAN may be under ISO 13616, and the narrowest in use. */
    private static final int ABSOLUTE_MIN = 15;
    private static final int ABSOLUTE_MAX = 34;

    private Iban() {
    }

    /**
     * Spaces are how people write IBANs and not how they are stored.
     *
     * Printed in groups of four on every bank statement, so a pasted one
     * arrives with spaces in it. Stripping them is kinder than refusing.
     */
    public static String normalise(String iban) {

        if (iban == null) {
            return null;
        }

        return iban.replace(" ", "").replace(" ", "").trim().toUpperCase();
    }

    /**
     * Why this is not an IBAN, or null if it is one.
     *
     * Returns the reason rather than a boolean because every one of these is
     * something the person typing can act on, and "invalid IBAN" is not.
     */
    public static String reasonInvalid(String raw) {

        String iban = normalise(raw);

        if (iban == null || iban.isEmpty()) {
            return "An IBAN is required";
        }

        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+")) {
            return "An IBAN starts with two letters for the country and two "
                    + "check digits, then the account number";
        }

        if (iban.length() < ABSOLUTE_MIN || iban.length() > ABSOLUTE_MAX) {
            return "An IBAN is between %d and %d characters; this one is %d"
                    .formatted(ABSOLUTE_MIN, ABSOLUTE_MAX, iban.length());
        }

        String country = iban.substring(0, 2);
        Integer expected = LENGTHS.get(country);

        if (expected != null && iban.length() != expected) {
            return "A %s IBAN is %d characters; this one is %d".formatted(
                    country, expected, iban.length()
            );
        }

        if (mod97(iban) != 1) {
            return "The check digits do not match the rest of the IBAN, so "
                    + "something in it has been mistyped";
        }

        return null;
    }

    public static boolean isValid(String raw) {
        return reasonInvalid(raw) == null;
    }

    /** Whether this IBAN belongs to the given country. */
    public static boolean isFromCountry(String raw, String country) {

        String iban = normalise(raw);

        return iban != null
                && iban.length() >= 2
                && iban.startsWith(country);
    }

    /**
     * ISO 7064 MOD-97-10, which a valid IBAN leaves at 1.
     *
     * The first four characters move to the end and letters become numbers
     * from A=10, giving a number far too big for a long -- so the remainder is
     * carried digit by digit instead.
     */
    private static int mod97(String iban) {

        String rearranged = iban.substring(4) + iban.substring(0, 4);

        int remainder = 0;

        for (int i = 0; i < rearranged.length(); i++) {

            char character = rearranged.charAt(i);

            if (character >= '0' && character <= '9') {
                remainder = (remainder * 10 + (character - '0')) % 97;
            } else {
                int value = character - 'A' + 10;
                remainder = (remainder * 100 + value) % 97;
            }
        }

        return remainder;
    }
}
