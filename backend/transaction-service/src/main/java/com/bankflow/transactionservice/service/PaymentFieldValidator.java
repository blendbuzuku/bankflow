package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.iban.Iban;
import com.bankflow.common.geo.Country;
import com.bankflow.common.iban.SchemeText;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.entity.PaymentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Refuses a payment whose fields could never be carried, before anything moves.
 *
 * Every rule here exists because something downstream enforces it and answers
 * later, or worse, does not enforce it at all. ISO 20022 types IBAN loosely
 * enough that a wrong-length one passes XSD validation, reaches KIPS, and
 * comes back days afterwards as a rejection the customer has been waiting on.
 * A length limit exceeded fails the schema at send time, after the debtor has
 * already been debited into suspense.
 *
 * One class rather than annotations on the request, because several of the
 * rules depend on the rail: a KIPS payment has to be domestic, an
 * international one does not, and a bean validator cannot see that.
 */
@Component
public class PaymentFieldValidator {

    /** ISO 20022 maximum lengths for the elements a payment fills in. */
    private static final int NAME_MAX = 70;
    private static final int REMITTANCE_MAX = 140;
    private static final int REFERENCE_MAX = 35;

    private static final String BIC_PATTERN =
            "^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$";

    /**
     * Checks everything about a payment that does not depend on balances.
     *
     * @param external whether this leaves the bank, which decides if a
     *                 creditor IBAN and agent are required at all
     */
    public void validate(TransferRequest request, PaymentType rail, boolean external) {

        checkAmount(request.getAmount());

        checkReference(request.getEndToEndId(), "The end-to-end reference");
        checkReference(request.getInstructionId(), "The instruction reference");

        checkText(request.getCreditorName(), "The beneficiary name", NAME_MAX);
        checkText(request.getDebtorName(), "The payer name", NAME_MAX);
        checkText(
                request.getRemittanceInformation(),
                "The payment reference",
                REMITTANCE_MAX
        );

        if (!external) {
            return;
        }

        checkCreditorIban(request.getCreditorIban(), rail);
        checkBic(request.getCreditorAgentBic());
        checkCreditorCountry(request.getCreditorCountry(), rail);
    }

    /**
     * Money has two decimal places.
     *
     * A third one is not rounded away quietly: the ledger stores two, so
     * accepting 10.005 would book a different figure from the one somebody
     * authorised, and the difference would surface as a reconciliation break
     * nobody could trace back to a form.
     */
    private void checkAmount(BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "The amount must be greater than zero"
            );
        }

        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(
                    "The amount cannot be more precise than a cent; %s has %d "
                            .formatted(
                                    amount.toPlainString(),
                                    amount.stripTrailingZeros().scale()
                            )
                            + "decimal places"
            );
        }
    }

    private void checkCreditorIban(String iban, PaymentType rail) {

        String reason = Iban.reasonInvalid(iban);

        if (reason != null) {
            throw new BusinessException(
                    "The beneficiary IBAN is not usable. " + reason + "."
            );
        }

        /*
         * KIPS clears Kosovo only. A foreign IBAN on a domestic rail is not a
         * payment that gets rejected later -- it is one the scheme has no way
         * to route at all, so it is refused here with the reason rather than
         * sent to find out.
         */
        if (isDomesticRail(rail) && !Iban.isFromCountry(iban, "XK")) {

            throw new BusinessException(
                    ("KIPS clears Kosovo payments only, and %s is not a Kosovo "
                            + "IBAN. Send it as an international payment "
                            + "instead.").formatted(Iban.normalise(iban))
            );
        }
    }

    /**
     * Where the money is going.
     *
     * Checked against the rail for the same reason the IBAN is: KIPS clears
     * Kosovo, so a domestic payment addressed to another country is not a
     * payment that gets rejected downstream, it is one nobody can route.
     */
    private void checkCreditorCountry(String country, PaymentType rail) {

        if (country == null || country.isBlank()) {
            throw new BusinessException(
                    "Say which country the money is going to. It is what "
                            + "screening a destination has to run on"
            );
        }

        String reason = Country.reasonInvalid(country, "The beneficiary country");

        if (reason != null) {
            throw new BusinessException(reason);
        }

        if (isDomesticRail(rail) && !"XK".equalsIgnoreCase(country.trim())) {

            throw new BusinessException(
                    ("KIPS clears Kosovo payments only, so a payment on this "
                            + "rail cannot be going to %s. Send it as an "
                            + "international payment instead.")
                            .formatted(country.trim().toUpperCase())
            );
        }
    }

    private void checkBic(String bic) {

        if (bic == null || bic.isBlank()) {
            throw new BusinessException(
                    "The beneficiary's bank is required to route the payment"
            );
        }

        if (!bic.trim().toUpperCase().matches(BIC_PATTERN)) {
            throw new BusinessException(
                    ("%s is not a BIC. A BIC is 8 or 11 characters: four for "
                            + "the bank, two for the country, two for the "
                            + "location, and optionally three for a branch.")
                            .formatted(bic)
            );
        }
    }

    /**
     * References travel unchanged and are what both banks quote at each other,
     * so a character the scheme cannot carry makes a payment unquotable.
     */
    private void checkReference(String value, String label) {

        if (value == null || value.isBlank()) {
            throw new BusinessException(label + " is required");
        }

        String reason = SchemeText.reasonInvalid(value, label, REFERENCE_MAX);

        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private void checkText(String value, String label, int max) {

        String reason = SchemeText.reasonInvalid(value, label, max);

        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private boolean isDomesticRail(PaymentType rail) {
        return rail == PaymentType.KIPS_ACH || rail == PaymentType.KIPS_RTGS;
    }
}
