 package com.bankflow.accountservice.service;

import com.bankflow.accountservice.audit.AuditService;
import com.bankflow.accountservice.dto.BeneficialOwnerRequest;
import com.bankflow.accountservice.entity.BeneficialOwner;
import com.bankflow.common.contact.Phone;
import com.bankflow.common.geo.Country;
import com.bankflow.common.audit.AuditEventType;
import com.bankflow.accountservice.dto.ClientCreateRequest;
import com.bankflow.accountservice.dto.ClientResponse;
import com.bankflow.accountservice.entity.BusinessClient;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.entity.ClientType;
import com.bankflow.accountservice.entity.IndividualClient;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.accountservice.security.AuthenticatedUser;
import com.bankflow.accountservice.security.SecurityUtils;
import com.bankflow.accountservice.entity.ClientStatus;
import com.bankflow.common.exception.BusinessException;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.bankflow.accountservice.entity.IdentityDocumentType;
import com.bankflow.accountservice.entity.LegalForm;
import java.time.LocalDate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final AuditService auditService;
    private final ClientFieldValidator fieldValidator;
    private final SignatoryService signatoryService;

    public ClientService(
            ClientRepository clientRepository,
            AuditService auditService,
            ClientFieldValidator fieldValidator,
            SignatoryService signatoryService) {

        this.clientRepository = clientRepository;
        this.auditService = auditService;
        this.fieldValidator = fieldValidator;
        this.signatoryService = signatoryService;
    }

    /**
     * Self-registration.
     *
     * The client is bound to the caller, deliberately: a userId is never taken
     * from the request, or anyone could register a client against someone
     * else's login. Staff onboarding a customer at the counter use
     * {@link #createClientFor} instead.
     */
    @Transactional
    public ClientResponse createClient(ClientCreateRequest request) {

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();

        if (clientRepository.findByUserId(currentUser.userId()).isPresent()) {
            throw new DuplicateResourceException(
                    "You are already registered as a client"
            );
        }

        return persist(request, currentUser.userId());
    }

    private ClientResponse persist(ClientCreateRequest request, Long userId) {

        /*
         * Checked before the duplicate lookup, so somebody typing a malformed
         * address is told that rather than being told it is already taken.
         */
        fieldValidator.validate(request);

        if (clientRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Client with this email already exists"
            );
        }

        Client client;

        if (request.getClientType() == ClientType.INDIVIDUAL) {

            IndividualClient individual = new IndividualClient();

            individual.setFirstName(request.getFirstName().trim());
            individual.setLastName(request.getLastName().trim());
            individual.setDateOfBirth(request.getDateOfBirth());

            individual.setPlaceOfBirth(trimmed(request.getPlaceOfBirth()));
            individual.setCountryOfBirth(Country.normalise(request.getCountryOfBirth()));
            individual.setNationality(Country.normalise(request.getNationality()));
            individual.setCountryOfResidence(
                    Country.normalise(request.getCountryOfResidence()));

            individual.setIdentityDocumentType(request.getIdentityDocumentType());
            individual.setIdentityDocumentNumber(
                    upper(request.getIdentityDocumentNumber()));
            individual.setIdentityDocumentCountry(
                    Country.normalise(request.getIdentityDocumentCountry()));
            individual.setIdentityDocumentExpiry(request.getIdentityDocumentExpiry());
            individual.setPersonalNumber(trimmed(request.getPersonalNumber()));

            client = individual;

        } else if (request.getClientType() == ClientType.BUSINESS) {

            BusinessClient business = new BusinessClient();

            business.setLegalName(request.getLegalName());
            business.setRegistrationNumber(
                    request.getRegistrationNumber()
            );
            business.setTaxNumber(upper(request.getTaxNumber()));
            business.setIndustry(trimmed(request.getIndustry()));

            business.setLegalForm(request.getLegalForm());
            business.setDateOfIncorporation(request.getDateOfIncorporation());
            business.setNaceCode(upper(request.getNaceCode()));

            /*
             * Added through the entity so the cascade writes the foreign key.
             * A company with no owners never reaches here -- the validator
             * refuses it -- so an empty list would be a bug, not a business
             * case.
             */
            for (BeneficialOwnerRequest declared : request.getBeneficialOwners()) {
                business.addBeneficialOwner(toOwner(declared));
            }

            client = business;

        } else {

            throw new IllegalArgumentException(
                    "Unsupported client type"
            );
        }

        client.setUserId(userId);
        client.setEmail(request.getEmail().trim().toLowerCase());

        /*
         * Stored dialled, not written. A number kept as "044 123 456" is
         * meaningless to anyone outside Kosovo, and this bank's customers are
         * routinely outside Kosovo.
         */
        client.setPhone(Phone.normalise(request.getPhone()));

        client.setAddressLine1(trimmed(request.getAddressLine1()));
        client.setAddressLine2(trimmed(request.getAddressLine2()));
        client.setCity(trimmed(request.getCity()));
        client.setPostalCode(trimmed(request.getPostalCode()));
        client.setCountry(Country.normalise(request.getCountry()));

        client.setPoliticallyExposed(request.isPoliticallyExposed());
        client.setPepDetails(trimmed(request.getPepDetails()));
        client.setSourceOfFunds(request.getSourceOfFunds());
        client.setSourceOfFundsDetail(trimmed(request.getSourceOfFundsDetail()));

        /*
         * A new client is unapproved until someone has checked who they are.
         * Nothing can be opened for them until a member of staff activates them.
         */
        client.setStatus(ClientStatus.PENDING.name());

        Client saved = clientRepository.save(client);

        /*
         * The registrant becomes the client's first signatory, so there is one
         * rule for resolving a login rather than a special case for people and
         * another for companies.
         */
        signatoryService.recordRegistrant(saved.getId(), userId);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("clientType", request.getClientType());
        details.put("email", request.getEmail());
        details.put("boundToUserId", userId);
        details.put("status", ClientStatus.PENDING.name());

        auditService.record(
                AuditEventType.CLIENT_REGISTERED,
                "Client",
                String.valueOf(saved.getId()),
                "%s registered as a client, pending approval".formatted(
                        request.getEmail()),
                details
        );

        return mapToResponse(saved);
    }

    /** The client book. Staff only — this is the whole customer base. */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public List<ClientResponse> getAllClients() {

        return clientRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ClientResponse getClientById(Long id) {

        Client client =
                clientRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found with id: " + id
                                )
                        );

        requireStaffOrOwner(client);

        return mapToResponse(client);
    }

    /**
     * Guards one client's details.
     *
     * Staff need to see anyone to do their job; a customer may see only
     * themselves. Without this, any signed-in customer could walk the id range
     * and read the name, address and personal number of every client the bank
     * has.
     */
    private void requireStaffOrOwner(Client client) {

        if (isStaff()) {
            return;
        }

        if (!SecurityUtils.getCurrentUserId().equals(client.getUserId())) {
            throw new AccessDeniedException("That client is not yours to view");
        }
    }

    /**
     * The bank's own entity, seeded with a negative user id so it can never
     * collide with a real login.
     */
    private static boolean isInternal(Client client) {
        return client.getUserId() != null && client.getUserId() < 0;
    }

    private boolean isStaff() {

        return SecurityUtils.hasRole("TELLER")
                || SecurityUtils.hasRole("OPERATIONS")
                || SecurityUtils.hasRole("BANK_ADMIN");
    }

    /**
     * Approves a client after their identity has been checked.
     *
     * A client registers as PENDING and stays there until a member of staff has
     * actually seen who they are. Nothing else can be done for them until this
     * happens — accounts cannot be opened for an unapproved client, which is
     * what makes the status mean something rather than being decoration.
     */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public ClientResponse activate(Long clientId) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId
                ));

        if (ClientStatus.ACTIVE.name().equals(client.getStatus())) {
            throw new BusinessException("This client is already approved");
        }

        if (ClientStatus.CLOSED.name().equals(client.getStatus())) {
            throw new BusinessException("A closed client cannot be reopened");
        }

        String previous = client.getStatus();

        client.setStatus(ClientStatus.ACTIVE.name());

        Client saved = clientRepository.save(client);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("previousStatus", previous);
        details.put("status", ClientStatus.ACTIVE.name());
        details.put("email", saved.getEmail());
        details.put("boundToUserId", saved.getUserId());

        /*
         * The actor is not passed in: AuditService takes it from the security
         * context, so the name on the record is the one that authenticated.
         */
        auditService.record(
                AuditEventType.CLIENT_APPROVED,
                "Client",
                String.valueOf(saved.getId()),
                "Client %s approved and may now be given accounts".formatted(
                        saved.getEmail()),
                details
        );

        return mapToResponse(saved);
    }

    /**
     * Suspends a client. Their accounts remain, but nothing new can be opened.
     */
    @PreAuthorize("hasAnyRole('OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public ClientResponse suspend(Long clientId) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client not found with id: " + clientId
                ));

        String previous = client.getStatus();

        client.setStatus(ClientStatus.SUSPENDED.name());

        Client saved = clientRepository.save(client);

        Map<String, Object> details = new LinkedHashMap<>();

        details.put("previousStatus", previous);
        details.put("status", ClientStatus.SUSPENDED.name());
        details.put("email", saved.getEmail());

        auditService.record(
                AuditEventType.CLIENT_SUSPENDED,
                "Client",
                String.valueOf(saved.getId()),
                "Client %s suspended; nothing further can be opened for them"
                        .formatted(saved.getEmail()),
                details
        );

        return mapToResponse(saved);
    }

    /**
     * Registers a client on behalf of someone at the counter.
     *
     * {@link #createClient} binds the new client to whoever is calling, which is
     * right for self-registration and useless for a teller — the client would
     * end up attached to the teller's own login. This takes the user explicitly
     * so staff can onboard a customer who is standing in front of them.
     */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    @Transactional
    public ClientResponse createClientFor(Long userId, ClientCreateRequest request) {

        if (userId == null) {
            throw new BusinessException("A user must be given");
        }

        if (clientRepository.findByUserId(userId).isPresent()) {
            throw new DuplicateResourceException(
                    "That user is already registered as a client"
            );
        }

        return persist(request, userId);
    }

    public ClientResponse getCurrentClient() {

        Long userId = SecurityUtils.getCurrentUserId();

        /*
         * Through the signatory table, so somebody added to a company account
         * sees the company rather than being told they have no profile.
         */
        Client client = signatoryService.clientIdFor(userId)
                .flatMap(clientRepository::findById)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No client profile exists for the current user"
                        )
                );

        return mapToResponse(client);
    }

    /**
     * Lookup by email. Staff only: open to customers it would be an address
     * oracle, answering "does this person bank here?" for anyone who asks.
     */
    @PreAuthorize("hasAnyRole('TELLER', 'OPERATIONS', 'BANK_ADMIN')")
    public ClientResponse getClientByEmail(String email) {

        Client client =
                clientRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found with email: " + email
                                )
                        );

        return mapToResponse(client);
    }

    /** Correcting details: staff for anyone, a customer for themselves only. */
    @Transactional
    public ClientResponse updateClient(
            Long id,
            ClientCreateRequest request) {

        Client client =
                clientRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found with id: " + id
                                )
                        );

        requireStaffOrOwner(client);

        if (!client.getEmail().equals(request.getEmail())
                && clientRepository.existsByEmail(request.getEmail())) {

            throw new DuplicateResourceException(
                    "Client with this email already exists"
            );
        }

        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());

        if (client instanceof IndividualClient individual) {

            individual.setFirstName(
                    request.getFirstName()
            );

            individual.setLastName(
                    request.getLastName()
            );

            individual.setDateOfBirth(
                    request.getDateOfBirth()
            );

        } else if (client instanceof BusinessClient business) {

            business.setLegalName(
                    request.getLegalName()
            );

            business.setRegistrationNumber(
                    request.getRegistrationNumber()
            );

            business.setTaxNumber(
                    request.getTaxNumber()
            );

            business.setIndustry(
                    request.getIndustry()
            );
        }

        Client updatedClient =
                clientRepository.save(client);

        return mapToResponse(updatedClient);
    }

    /**
     * Removes a registration that never became a customer.
     *
     * A bank does not delete clients it has done business with — the accounts,
     * the payments and the audit trail behind them have to stay, so an ex
     * customer is suspended, not erased. Only a PENDING client can go, and by
     * the approval gate a PENDING client has never held an account.
     */
    @PreAuthorize("hasRole('BANK_ADMIN')")
    @Transactional
    public void deleteClient(Long id) {

        Client client =
                clientRepository.findById(id)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Client not found with id: " + id
                                )
                        );

        if (!ClientStatus.PENDING.name().equals(client.getStatus())) {
            throw new BusinessException(
                    ("%s has been a customer of the bank and cannot be deleted."
                            + " Suspend them instead.")
                            .formatted(client.getDisplayName()));
        }

        clientRepository.delete(client);
    }

    private ClientResponse mapToResponse(Client client) {

        String firstName = null;
        String lastName = null;

        String legalName = null;
        String registrationNumber = null;
        String taxNumber = null;
        String industry = null;

        String placeOfBirth = null;
        String countryOfBirth = null;
        String nationality = null;
        String countryOfResidence = null;

        IdentityDocumentType documentType = null;
        String documentNumber = null;
        String documentCountry = null;
        LocalDate documentExpiry = null;

        LegalForm legalForm = null;
        LocalDate incorporated = null;
        String naceCode = null;
        List<ClientResponse.BeneficialOwnerResponse> owners = List.of();

        if (client instanceof IndividualClient individual) {

            firstName = individual.getFirstName();
            lastName = individual.getLastName();

            placeOfBirth = individual.getPlaceOfBirth();
            countryOfBirth = individual.getCountryOfBirth();
            nationality = individual.getNationality();
            countryOfResidence = individual.getCountryOfResidence();

            documentType = individual.getIdentityDocumentType();
            documentNumber = maskDocument(individual.getIdentityDocumentNumber());
            documentCountry = individual.getIdentityDocumentCountry();
            documentExpiry = individual.getIdentityDocumentExpiry();

        } else if (client instanceof BusinessClient business) {

            legalName = business.getLegalName();
            registrationNumber =
                    business.getRegistrationNumber();
            taxNumber =
                    business.getTaxNumber();
            industry =
                    business.getIndustry();

            legalForm = business.getLegalForm();
            incorporated = business.getDateOfIncorporation();
            naceCode = business.getNaceCode();

            owners = business.getBeneficialOwners()
                    .stream()
                    .map(owner -> new ClientResponse.BeneficialOwnerResponse(
                            owner.getId(),
                            owner.getFullName(),
                            owner.getDateOfBirth(),
                            owner.getNationality(),
                            owner.getCountryOfResidence(),
                            owner.getOwnershipPercentage(),
                            owner.isControlsByOtherMeans(),
                            owner.isPoliticallyExposed()
                    ))
                    .toList();
        }

        return new ClientResponse(
                client.getId(),
                client.getClientType(),

                firstName,
                lastName,
                client.getEmail(),
                client.getPhone(),

                client instanceof IndividualClient individual
                        ? individual.getDateOfBirth()
                        : null,

                client.getStatus(),
                isInternal(client),

                legalName,
                registrationNumber,
                taxNumber,
                industry,

                client.getAddressLine1(),
                client.getAddressLine2(),
                client.getCity(),
                client.getPostalCode(),
                client.getCountry(),

                placeOfBirth,
                countryOfBirth,
                nationality,
                countryOfResidence,

                documentType,
                documentNumber,
                documentCountry,
                documentExpiry,

                client.isPoliticallyExposed(),
                client.getPepDetails(),
                client.getSourceOfFunds(),

                legalForm,
                incorporated,
                naceCode,
                owners,

                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }


    /**
     * Shows enough of a document number to confirm it, and no more.
     *
     * A teller comparing the passport in front of them against the record
     * needs the last few characters; nobody needs the whole number on a
     * screen, and a full one there is a full one in a screenshot.
     */
    private String maskDocument(String number) {

        if (number == null || number.length() <= 4) {
            return number;
        }

        return "*".repeat(number.length() - 4)
                + number.substring(number.length() - 4);
    }

    private BeneficialOwner toOwner(BeneficialOwnerRequest declared) {

        BeneficialOwner owner = new BeneficialOwner();

        owner.setFullName(declared.getFullName().trim());
        owner.setDateOfBirth(declared.getDateOfBirth());
        owner.setNationality(Country.normalise(declared.getNationality()));
        owner.setCountryOfResidence(
                Country.normalise(declared.getCountryOfResidence()));
        owner.setOwnershipPercentage(declared.getOwnershipPercentage());
        owner.setControlsByOtherMeans(declared.isControlsByOtherMeans());
        owner.setPoliticallyExposed(declared.isPoliticallyExposed());

        return owner;
    }

    /** Blank and whitespace mean the same as absent for every optional field. */
    private String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Document, tax and sector codes are compared, so they are stored upper. */
    private String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
