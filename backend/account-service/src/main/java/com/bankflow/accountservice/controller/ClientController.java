package com.bankflow.accountservice.controller;

import com.bankflow.accountservice.dto.ClientCreateRequest;
import com.bankflow.accountservice.dto.ClientResponse;
import com.bankflow.accountservice.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(
            @Valid @RequestBody ClientCreateRequest request) {

        ClientResponse response =
                clientService.createClient(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> getAllClients() {

        return ResponseEntity.ok(
                clientService.getAllClients()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> getClientById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                clientService.getClientById(id)
        );
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ClientResponse> getClientByEmail(
            @PathVariable String email) {

        return ResponseEntity.ok(
                clientService.getClientByEmail(email)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> updateClient(
            @PathVariable Long id,
            @Valid @RequestBody ClientCreateRequest request) {

        return ResponseEntity.ok(
                clientService.updateClient(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @PathVariable Long id) {

        clientService.deleteClient(id);

        return ResponseEntity.noContent().build();
    }
}