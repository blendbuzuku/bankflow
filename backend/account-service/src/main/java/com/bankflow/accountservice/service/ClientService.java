 package com.bankflow.accountservice.service;

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

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
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

        if (clientRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Client with this email already exists"
            );
        }

        Client client;

        if (request.getClientType() == ClientType.INDIVIDUAL) {

            IndividualClient individual = new IndividualClient();

            individual.setFirstName(request.getFirstName());
            individual.setLastName(request.getLastName());
            individual.setDateOfBirth(request.getDateOfBirth());

            client = individual;

        } else if (request.getClientType() == ClientType.BUSINESS) {

            BusinessClient business = new BusinessClient();

            business.setLegalName(request.getLegalName());
            business.setRegistrationNumber(
                    request.getRegistrationNumber()
            );
            business.setTaxNumber(request.getTaxNumber());
            business.setIndustry(request.getIndustry());

            client = business;

        } else {

            throw new IllegalArgumentException(
                    "Unsupported client type"
            );
        }

        client.setUserId(userId);
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());

        /*
         * A new client is unapproved until someone has checked who they are.
         * Nothing can be opened for them until a member of staff activates them.
         */
        client.setStatus(ClientStatus.PENDING.name());

        return mapToResponse(clientRepository.save(client));
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

        client.setStatus(ClientStatus.ACTIVE.name());

        return mapToResponse(clientRepository.save(client));
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

        client.setStatus(ClientStatus.SUSPENDED.name());

        return mapToResponse(clientRepository.save(client));
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

        Client client =
                clientRepository.findByUserId(userId)
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

        if (client instanceof IndividualClient individual) {

            firstName = individual.getFirstName();
            lastName = individual.getLastName();

        } else if (client instanceof BusinessClient business) {

            legalName = business.getLegalName();
            registrationNumber =
                    business.getRegistrationNumber();
            taxNumber =
                    business.getTaxNumber();
            industry =
                    business.getIndustry();
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

                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}