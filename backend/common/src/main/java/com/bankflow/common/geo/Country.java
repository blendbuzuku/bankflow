package com.bankflow.common.geo;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ISO 3166-1 alpha-2, the only country notation the scheme accepts.
 *
 * Two letters rather than a name, because "Kosovo", "Republic of Kosovo" and
 * "Kosova" are the same place and none of them fits a Ctry element. Taken from
 * the JDK's own list so it stays current without a table to maintain, with XK
 * added: Kosovo has a reserved user-assigned code that is used throughout
 * banking and payments but is not in the official standard.
 */
public final class Country {

    private static final Set<String> CODES = buildCodes();

    private Country() {
    }

    private static Set<String> buildCodes() {

        Set<String> codes = Set.of(Locale.getISOCountries())
                .stream()
                .collect(Collectors.toSet());

        codes = new java.util.HashSet<>(codes);

        /*
         * XK is user-assigned rather than official. Every bank, IBAN registry
         * and payment scheme in the region uses it anyway, and a validator
         * that refused it would refuse this bank's own country.
         */
        codes.add("XK");

        return Set.copyOf(codes);
    }

    public static boolean isValid(String code) {

        return code != null
                && code.length() == 2
                && CODES.contains(code.trim().toUpperCase());
    }

    /** Why this is not a country code, or null if it is one. */
    public static String reasonInvalid(String code, String label) {

        if (code == null || code.isBlank()) {
            return null;
        }

        if (!isValid(code)) {
            return "%s must be a two-letter country code such as XK, AL or DE; "
                    .formatted(label) + "\"" + code.trim() + "\" is not one";
        }

        return null;
    }

    public static String normalise(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }
}
