package com.bankflow.accountservice.service;

import com.bankflow.accountservice.dto.ClientCreateRequest;
import com.bankflow.accountservice.dto.ClientResponse;
import com.bankflow.accountservice.entity.BusinessClient;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.entity.ClientType;
import com.bankflow.accountservice.entity.IndividualClient;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public ClientResponse createClient(ClientCreateRequest request) {

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

        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());

        Client savedClient = clientRepository.save(client);

        return mapToResponse(savedClient);
    }

    public List<ClientResponse> getAllClients() {

        return clientRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ClientResponse getClientById(Long id) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + id
                        )
                );

        return mapToResponse(client);
    }

    public ClientResponse getClientByEmail(String email) {

        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with email: " + email
                        )
                );

        return mapToResponse(client);
    }

    public ClientResponse updateClient(
            Long id,
            ClientCreateRequest request) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + id
                        )
                );

        if (!client.getEmail().equals(request.getEmail())
                && clientRepository.existsByEmail(request.getEmail())) {

            throw new DuplicateResourceException(
                    "Client with this email already exists"
            );
        }

        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());

        if (client instanceof IndividualClient individual) {

            individual.setFirstName(request.getFirstName());
            individual.setLastName(request.getLastName());
            individual.setDateOfBirth(request.getDateOfBirth());

        } else if (client instanceof BusinessClient business) {

            business.setLegalName(request.getLegalName());
            business.setRegistrationNumber(
                    request.getRegistrationNumber()
            );
            business.setTaxNumber(request.getTaxNumber());
            business.setIndustry(request.getIndustry());
        }

        Client updatedClient =
                clientRepository.save(client);

        return mapToResponse(updatedClient);
    }

    public void deleteClient(Long id) {

        Client client = clientRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Client not found with id: " + id
                        )
                );

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
            registrationNumber = business.getRegistrationNumber();
            taxNumber = business.getTaxNumber();
            industry = business.getIndustry();
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
                legalName,
                registrationNumber,
                taxNumber,
                industry,
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }
}