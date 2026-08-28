package com.bankflow.accountservice.service;

import com.bankflow.accountservice.dto.BeneficialOwnerRequest;
import com.bankflow.accountservice.dto.ClientCreateRequest;
import com.bankflow.accountservice.entity.BeneficialOwner;
import com.bankflow.accountservice.entity.ClientType;
import com.bankflow.common.contact.Phone;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.geo.Country;
import com.bankflow.common.iban.SchemeText;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refuses a client record that could not belong to a real person or company.
 *
 * A bank is answerable for who it lets in, and every check here is one a
 * branch would make at the counter: that somebody is old enough to hold an
 * account, that the document they produced has not expired, that a telephone
 * number can be dialled if a payment has to be queried.
 *
 * Bean validation covers lengths and does it declaratively. What it cannot do
 * is anything conditional -- a person needs a date of birth and a company
 * needs owners -- so the rules that depend on the client type live here.
 */
@Component
public class ClientFieldValidator {

    /** The age at which somebody may hold an account in their own name. */
    private static final int MINIMUM_AGE = 18;

    /**
     * Beyond which a date of birth is a typo rather than a customer.
     *
     * The oldest verified human lived to 122. A birth year in the 1800s is
     * somebody who typed 1902 as 1092, and letting it through means the age
     * check silently passes on nonsense.
     */
    private static final int MAXIMUM_AGE = 120;

    /**
     * Letters, marks, spaces and the punctuation names actually contain.
     *
     * Unicode-aware on purpose: Behxhet Krasniqi and Gezim Cela are ordinary
     * Kosovo names, and a validator built around ASCII would reject a large
     * share of the customer base. Apostrophes and hyphens belong to names too
     * -- O'Neill, Bici-Krasniqi -- while digits do not.
     */
    private static final String NAME_PATTERN = "[\\p{L}\\p{M}][\\p{L}\\p{M}'\\-. ]*";

    private static final String DOCUMENT_PATTERN = "[A-Za-z0-9\\-]{4,40}";
    private static final String REGISTRATION_PATTERN = "[A-Za-z0-9\\-]{5,20}";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public void validate(ClientCreateRequest request) {

        validateEmail(request.getEmail());
        validatePhone(request.getPhone());
        validateAddress(request);
        validateRisk(request);

        if (request.getClientType() == ClientType.INDIVIDUAL) {
            validateIndividual(request);
        } else {
            validateBusiness(request);
        }
    }

    // --- everybody -----------------------------------------------------

    /**
     * Where the client is.
     *
     * Required rather than optional, for a concrete reason as much as a
     * regulatory one: the scheme's pacs.008 carries the debtor's postal
     * address, and a payment that omits it gives the receiving bank less to
     * identify the payer by than the rules assume it will have.
     */
    private void validateAddress(ClientCreateRequest request) {

        if (isBlank(request.getAddressLine1())) {
            throw new BusinessException(
                    "A street address is required. It travels with every "
                            + "payment this client makes"
            );
        }

        if (isBlank(request.getCity())) {
            throw new BusinessException("A town or city is required");
        }

        if (isBlank(request.getCountry())) {
            throw new BusinessException("A country is required");
        }

        checkCountry(request.getCountry(), "Country");

        /*
         * The address goes into a restricted-character element, so a street
         * name the scheme will not carry has to be caught at the counter
         * rather than when the customer's first payment is built.
         */
        schemeSafe(request.getAddressLine1(), "The street address", 70);
        schemeSafe(request.getAddressLine2(), "The second address line", 70);
        schemeSafe(request.getCity(), "The town or city", 35);
        schemeSafe(request.getPostalCode(), "The postal code", 16);
    }

    /**
     * Politically exposed status, and where the money comes from.
     *
     * A declared source of funds earns its place as a baseline: it is what
     * later activity gets compared against, and an account without one has
     * nothing for unusual activity to look unusual against.
     */
    private void validateRisk(ClientCreateRequest request) {

        if (request.getSourceOfFunds() == null) {
            throw new BusinessException(
                    "State where the money in this account is expected to "
                            + "come from"
            );
        }

        if (request.isPoliticallyExposed() && isBlank(request.getPepDetails())) {
            throw new BusinessException(
                    "A politically exposed client needs the office or "
                            + "relationship recorded. That is what the extra "
                            + "scrutiny is based on"
            );
        }
    }

    /**
     * Stricter than the annotation it backs up.
     *
     * Jakarta's @Email accepts "a@b", which is syntactically an address and
     * will never receive a statement. A bank emails people about their money,
     * so an address that cannot be delivered to is worth refusing at the
     * counter rather than discovering from a bounce.
     */
    private void validateEmail(String email) {

        if (isBlank(email)) {
            throw new BusinessException("An email address is required");
        }

        String trimmed = email.trim();

        if (!trimmed.matches("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+")) {
            throw new BusinessException(
                    "%s is not an email address that could receive mail"
                            .formatted(trimmed)
            );
        }

        if (trimmed.length() > 254) {
            throw new BusinessException(
                    "An email address is at most 254 characters"
            );
        }
    }

    private void validatePhone(String phone) {

        if (isBlank(phone)) {
            throw new BusinessException(
                    "A telephone number is required. A bank has to be able to "
                            + "reach somebody about a payment it has stopped"
            );
        }

        String reason = Phone.reasonInvalid(phone);

        if (reason != null) {
            throw new BusinessException(reason + ".");
        }
    }

    // --- a person ------------------------------------------------------

    private void validateIndividual(ClientCreateRequest request) {

        validateName(request.getFirstName(), "First name");
        validateName(request.getLastName(), "Last name");
        validateDateOfBirth(request.getDateOfBirth());
        validateIdentityDocument(request);
        validateOrigins(request);
    }

    private void validateName(String name, String label) {

        if (isBlank(name)) {
            throw new BusinessException(label + " is required");
        }

        String trimmed = name.trim();

        if (trimmed.length() < 2) {
            throw new BusinessException(label + " is too short to be a name");
        }

        if (!trimmed.matches(NAME_PATTERN)) {
            throw new BusinessException(
                    ("%s contains something that is not part of a name. "
                            + "Letters, spaces, apostrophes and hyphens only.")
                            .formatted(label)
            );
        }
    }

    /**
     * A date of birth is not optional for a person.
     *
     * It is how a bank tells two customers with the same name apart, it is
     * what the age check runs on, and the funds transfer rules allow it as an
     * identifier that travels with a payment when an address does not.
     */
    private void validateDateOfBirth(LocalDate dateOfBirth) {

        if (dateOfBirth == null) {
            throw new BusinessException(
                    "A date of birth is required to open an account"
            );
        }

        LocalDate today = LocalDate.now();

        if (!dateOfBirth.isBefore(today)) {
            throw new BusinessException(
                    "A date of birth cannot be today or in the future"
            );
        }

        int age = Period.between(dateOfBirth, today).getYears();

        if (age < MINIMUM_AGE) {
            throw new BusinessException(
                    ("An account holder must be at least %d. This date of "
                            + "birth makes them %d. A minor's account is opened "
                            + "for them by a guardian, which this bank does not "
                            + "yet support.").formatted(MINIMUM_AGE, age)
            );
        }

        if (age > MAXIMUM_AGE) {
            throw new BusinessException(
                    ("A date of birth of %s makes this person %d years old, "
                            + "which is a typing mistake rather than a "
                            + "customer.").formatted(dateOfBirth, age)
            );
        }
    }

    /**
     * The document somebody actually put on the counter.
     *
     * Without it, "know your customer" is a form the customer filled in about
     * themselves. The expiry is checked because an expired document does not
     * identify anybody -- that is what expiry means.
     */
    private void validateIdentityDocument(ClientCreateRequest request) {

        if (request.getIdentityDocumentType() == null) {
            throw new BusinessException(
                    "Record which document was checked: a passport, national "
                            + "identity card, or residence permit"
            );
        }

        if (isBlank(request.getIdentityDocumentNumber())) {
            throw new BusinessException("The document's number is required");
        }

        if (!request.getIdentityDocumentNumber().trim().matches(DOCUMENT_PATTERN)) {
            throw new BusinessException(
                    "A document number is 4 to 40 letters, digits or hyphens"
            );
        }

        if (isBlank(request.getIdentityDocumentCountry())) {
            throw new BusinessException(
                    "Record which country issued the document. The same number "
                            + "means different things in different registers"
            );
        }

        checkCountry(request.getIdentityDocumentCountry(), "The issuing country");

        LocalDate expiry = request.getIdentityDocumentExpiry();

        if (expiry == null) {
            throw new BusinessException(
                    "Record when the document expires, so we know when to ask "
                            + "for a current one"
            );
        }

        if (!expiry.isAfter(LocalDate.now())) {
            throw new BusinessException(
                    ("That document expired on %s. An expired document does "
                            + "not identify anybody -- ask for a current one.")
                            .formatted(expiry)
            );
        }
    }

    /**
     * Nationality, residence, and where somebody was born.
     *
     * Nationality and residence are frequently different and both matter: one
     * drives sanctions screening, the other tax reporting. Place of birth is
     * the identifier a payment can carry when an address cannot.
     */
    private void validateOrigins(ClientCreateRequest request) {

        if (isBlank(request.getNationality())) {
            throw new BusinessException("A nationality is required");
        }

        if (isBlank(request.getCountryOfResidence())) {
            throw new BusinessException("A country of residence is required");
        }

        if (isBlank(request.getPlaceOfBirth())) {
            throw new BusinessException(
                    "A place of birth is required. With the date of birth it "
                            + "identifies the payer when an address will not"
            );
        }

        checkCountry(request.getNationality(), "Nationality");
        checkCountry(request.getCountryOfResidence(), "Country of residence");
        checkCountry(request.getCountryOfBirth(), "Country of birth");

        schemeSafe(request.getPlaceOfBirth(), "The place of birth", 35);

        if (!isBlank(request.getPersonalNumber())
                && !request.getPersonalNumber().trim().matches("[0-9]{8,20}")) {

            throw new BusinessException("A personal number is 8 to 20 digits");
        }
    }

    // --- a company -----------------------------------------------------

    private void validateBusiness(ClientCreateRequest request) {

        if (isBlank(request.getLegalName())) {
            throw new BusinessException("A legal name is required for a company");
        }

        if (isBlank(request.getRegistrationNumber())) {
            throw new BusinessException(
                    "A business registration number is required. It is what "
                            + "ties this account to a company that exists"
            );
        }

        String registration = request.getRegistrationNumber().trim();

        if (!registration.matches(REGISTRATION_PATTERN)) {
            throw new BusinessException(
                    "A registration number is 5 to 20 letters, digits or "
                            + "hyphens; %s is not one".formatted(registration)
            );
        }

        String tax = request.getTaxNumber();

        if (!isBlank(tax) && !tax.trim().matches(REGISTRATION_PATTERN)) {
            throw new BusinessException(
                    "A tax number is 5 to 20 letters, digits or hyphens"
            );
        }

        if (request.getLegalForm() == null) {
            throw new BusinessException(
                    "Record how the company is constituted. It decides who can "
                            + "bind it"
            );
        }

        validateIncorporation(request.getDateOfIncorporation());
        validateNace(request.getNaceCode());
        validateBeneficialOwners(request.getBeneficialOwners());
    }

    private void validateIncorporation(LocalDate incorporated) {

        if (incorporated == null) {
            throw new BusinessException(
                    "A date of incorporation is required"
            );
        }

        if (incorporated.isAfter(LocalDate.now())) {
            throw new BusinessException(
                    "A company cannot have been incorporated in the future"
            );
        }
    }

    /** NACE rev. 2 is a letter and up to four digits, dotted after two. */
    private void validateNace(String nace) {

        if (isBlank(nace)) {
            return;
        }

        if (!nace.trim().toUpperCase().matches("[A-U](\\d{2}(\\.\\d{1,2})?)?")) {
            throw new BusinessException(
                    "A NACE code is a letter and up to four digits, such as "
                            + "G47.11; %s is not one".formatted(nace.trim())
            );
        }
    }

    /**
     * The people behind the company.
     *
     * The whole point of onboarding a company: an account is opened in a
     * company's name, but the risk belongs to the humans behind it, and a
     * shell exists precisely so those two look different.
     *
     * Declared shares are checked against each other as well as individually.
     * A company whose named owners hold more than all of it has been entered
     * wrongly; one whose named owners hold almost none of it has something
     * left to explain, and the disclosure threshold is what decides how much.
     */
    private void validateBeneficialOwners(List<BeneficialOwnerRequest> owners) {

        if (owners == null || owners.isEmpty()) {
            throw new BusinessException(
                    ("Name the people who own or control this company. Anyone "
                            + "holding %s%% or more has to be recorded -- an "
                            + "account belongs to somebody, and a company is "
                            + "not a somebody.")
                            .formatted(BeneficialOwner.DISCLOSURE_THRESHOLD
                                    .stripTrailingZeros().toPlainString())
            );
        }

        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();

        for (BeneficialOwnerRequest owner : owners) {

            validateName(owner.getFullName(), "The beneficial owner's name");
            validateDateOfBirth(owner.getDateOfBirth());

            checkCountry(owner.getNationality(), "The owner's nationality");
            checkCountry(
                    owner.getCountryOfResidence(), "The owner's country of residence"
            );

            BigDecimal share = owner.getOwnershipPercentage();

            if (share == null) {
                throw new BusinessException(
                        "State what share of the company %s holds"
                                .formatted(owner.getFullName())
                );
            }

            if (share.signum() < 0 || share.compareTo(HUNDRED) > 0) {
                throw new BusinessException(
                        "A shareholding is between 0 and 100 percent; %s has %s"
                                .formatted(owner.getFullName(), share.toPlainString())
                );
            }

            /*
             * Somebody at zero percent is only on the record if they control
             * the company another way. Otherwise they are not an owner and
             * listing them dilutes the disclosure.
             */
            if (share.signum() == 0 && !owner.isControlsByOtherMeans()) {
                throw new BusinessException(
                        ("%s is recorded as holding nothing and controlling "
                                + "the company by no other means, so they are "
                                + "not a beneficial owner.")
                                .formatted(owner.getFullName())
                );
            }

            String key = owner.getFullName().trim().toLowerCase()
                    + "|" + owner.getDateOfBirth();

            if (!seen.add(key)) {
                throw new BusinessException(
                        "%s is listed twice as a beneficial owner"
                                .formatted(owner.getFullName())
                );
            }

            total = total.add(share);
        }

        if (total.compareTo(HUNDRED) > 0) {
            throw new BusinessException(
                    ("The declared shareholdings add up to %s%%, which is more "
                            + "than the whole company.")
                            .formatted(total.stripTrailingZeros().toPlainString())
            );
        }
    }

    // --- shared --------------------------------------------------------

    private void checkCountry(String code, String label) {

        String reason = Country.reasonInvalid(code, label);

        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private void schemeSafe(String value, String label, int max) {

        String reason = SchemeText.reasonInvalid(value, label, max);

        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
