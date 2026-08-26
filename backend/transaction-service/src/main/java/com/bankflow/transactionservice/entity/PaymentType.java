package com.bankflow.transactionservice.entity;

import java.util.Arrays;
import java.util.Set;

/**
 * The rail a payment travels on.
 *
 * These are the schemes a Kosovo bank actually reaches: the two KIPS rails
 * operated by the Central Bank of Kosovo, plus correspondent banking for
 * anything leaving the country.
 *
 * The constraints attached to each rail are not house style — they are lifted
 * from the KIPS scheme schemas. ACH pins ChrgBr to SLEV and SvcLvl to SEPA and
 * will reject anything else at validation; RTGS permits the full ISO charge
 * bearer set. Encoding that here means a payment is rejected with a clear
 * message before we build XML that the scheme would refuse.
 */
public enum PaymentType {

    /**
     * Both parties bank with us. Settles on our own books, so no scheme and no
     * interbank message — though a pacs.008 is still generated and retained as
     * evidence of what the instruction was.
     *
     * That record is written in the ACH dialect, which means it has to carry
     * values the ACH schema accepts even though no clearing actually happens:
     * SttlmMtd and SvcLvl are both mandatory there, and each permits exactly
     * one value. Charge bearers are unrestricted because nothing external
     * constrains what we may charge our own customers.
     */
    INTERNAL(
            "INTL",
            "Internal transfer",
            false,
            ServiceLevel.SEPA,
            SettlementMethod.CLRG,
            Set.of(ChargeBearer.DEBT, ChargeBearer.CRED,
                    ChargeBearer.SHAR, ChargeBearer.SLEV)
    ),

    /**
     * KIPS ACH — the retail clearing rail. Bulk-cleared, next cycle settlement.
     * The scheme fixes the service level and charge bearer.
     */
    KIPS_ACH(
            "ACH",
            "KIPS retail clearing",
            true,
            ServiceLevel.SEPA,
            SettlementMethod.CLRG,
            Set.of(ChargeBearer.SLEV)
    ),

    /**
     * KIPS RTGS — real-time gross settlement, used for high-value payments.
     * Settles individually and immediately, and allows any charge bearer.
     */
    KIPS_RTGS(
            "RTGS",
            "KIPS real-time gross settlement",
            true,
            ServiceLevel.URGP,
            SettlementMethod.CLRG,
            Set.of(ChargeBearer.DEBT, ChargeBearer.CRED,
                    ChargeBearer.SHAR, ChargeBearer.SLEV)
    ),

    /**
     * Correspondent banking for payments leaving Kosovo.
     */
    INTERNATIONAL(
            "SWIFT",
            "International payment",
            true,
            ServiceLevel.NURG,
            SettlementMethod.INDA,
            Set.of(ChargeBearer.DEBT, ChargeBearer.CRED, ChargeBearer.SHAR)
    );

    private final String code;
    private final String displayName;
    private final boolean external;
    private final ServiceLevel serviceLevel;
    private final SettlementMethod settlementMethod;
    private final Set<ChargeBearer> permittedChargeBearers;

    PaymentType(
            String code,
            String displayName,
            boolean external,
            ServiceLevel serviceLevel,
            SettlementMethod settlementMethod,
            Set<ChargeBearer> permittedChargeBearers) {

        this.code = code;
        this.displayName = displayName;
        this.external = external;
        this.serviceLevel = serviceLevel;
        this.settlementMethod = settlementMethod;
        this.permittedChargeBearers = permittedChargeBearers;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether the payment leaves the institution. External payments need an
     * interbank message and a suspense leg; internal ones settle immediately.
     */
    public boolean isExternal() {
        return external;
    }

    /** Service level the scheme requires, or null for on-us payments. */
    public ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    public SettlementMethod getSettlementMethod() {
        return settlementMethod;
    }

    public Set<ChargeBearer> getPermittedChargeBearers() {
        return permittedChargeBearers;
    }

    public boolean permits(ChargeBearer chargeBearer) {
        return permittedChargeBearers.contains(chargeBearer);
    }

    /**
     * The charge bearer to use when the caller did not specify one. Where a
     * scheme allows only a single value, that value is the only sensible
     * default.
     */
    public ChargeBearer defaultChargeBearer() {

        return permittedChargeBearers.size() == 1
                ? permittedChargeBearers.iterator().next()
                : ChargeBearer.DEBT;
    }

    /** Which KIPS schema set validates this rail's messages. */
    public String schemaRail() {

        return switch (this) {
            case KIPS_ACH, INTERNAL -> "ach";
            case KIPS_RTGS, INTERNATIONAL -> "rtgs";
        };
    }

    public static PaymentType fromCode(String code) {

        return Arrays.stream(values())
                .filter(type -> type.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown payment type code: " + code
                        )
                );
    }
}
