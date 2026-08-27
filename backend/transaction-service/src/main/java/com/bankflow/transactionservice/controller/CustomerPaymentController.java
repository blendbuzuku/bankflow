package com.bankflow.transactionservice.controller;

import com.bankflow.transactionservice.dto.TransactionResponse;
import com.bankflow.transactionservice.dto.TransferRequest;
import com.bankflow.transactionservice.service.CustomerPaymentProperties;
import com.bankflow.transactionservice.service.CustomerPaymentService;
import jakarta.validation.Valid;
import com.bankflow.transactionservice.pacs.PacsMessage;
import com.bankflow.transactionservice.pacs.PacsMessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a customer can do with their own money.
 *
 * Everything here is scoped to the signed-in customer's own accounts. Staff use
 * the payment endpoints under /api/transactions instead, which can act on any
 * account.
 */
@RestController
@RequestMapping("/api/my")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPaymentController {

    private final CustomerPaymentService customerPaymentService;
    private final CustomerPaymentProperties properties;
    private final PacsMessageService pacsMessageService;

    public CustomerPaymentController(
            CustomerPaymentService customerPaymentService,
            CustomerPaymentProperties properties,
            PacsMessageService pacsMessageService) {

        this.customerPaymentService = customerPaymentService;
        this.properties = properties;
        this.pacsMessageService = pacsMessageService;
    }

    /**
     * What self-service allows, so the app can present the right form rather
     * than letting a customer fill one in and then be refused.
     */
    @GetMapping("/payment-limits")
    public ResponseEntity<Map<String, Object>> limits() {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("selfServiceEnabled", properties.isSelfServiceEnabled());

        result.put(
                "permittedRails",
                properties.getPermittedRails().stream()
                        .map(Enum::name)
                        .toList()
        );

        Map<String, Object> perCurrency = new LinkedHashMap<>();

        for (var currency : com.bankflow.transactionservice.entity.Currency.values()) {
            perCurrency.put(currency.name(), properties.limitFor(currency));
        }

        result.put("limits", perCurrency);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/payments")
    public ResponseEntity<TransactionResponse> pay(
            @Valid @RequestBody TransferRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(TransactionResponse.fromEntity(
                        customerPaymentService.send(request)
                ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> history() {

        return ResponseEntity.ok(
                customerPaymentService.ownHistory()
                        .stream()
                        .map(TransactionResponse::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/transactions/{transactionReference}")
    public ResponseEntity<TransactionResponse> one(
            @PathVariable String transactionReference) {

        return ResponseEntity.ok(
                TransactionResponse.fromEntity(
                        customerPaymentService.ownTransaction(transactionReference)
                )
        );
    }

    /**
     * The scheme messages behind one of the customer's own payments.
     *
     * Ownership is established from the payment first, so a customer sees the
     * messages for their money and no one else's. It is their payment: they are
     * entitled to see what was actually sent on their behalf.
     */
    @GetMapping("/transactions/{transactionReference}/messages")
    public ResponseEntity<List<PacsMessage>> messages(
            @PathVariable String transactionReference) {

        customerPaymentService.ownTransaction(transactionReference);

        return ResponseEntity.ok(
                pacsMessageService.findForTransaction(transactionReference)
        );
    }

    /**
     * One message body.
     *
     * The message is located first and the payment it belongs to is then
     * checked, so a guessed message id is refused rather than answered.
     */
    @GetMapping(
            value = "/messages/{messageId}/xml",
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> messageXml(@PathVariable String messageId) {

        PacsMessage message = pacsMessageService.findByMessageId(messageId);

        customerPaymentService.ownTransaction(message.getTransactionReference());

        return ResponseEntity.ok(message.getRawXml());
    }
}
