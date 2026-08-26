package com.bankflow.transactionservice.pacs;

import java.math.BigDecimal;

/**
 * The fields we act on from an inbound KIPS message.
 *
 * Deliberately flat and partial: this is not a full ISO object model, only the
 * values the payment engine needs to route, book and answer. The complete
 * message is retained as raw XML, which stays the record of what was received.
 *
 * @param messageType      which definition arrived
 * @param messageId        the sender's MsgId
 * @param endToEndId       payment reference, present on both message types
 * @param transactionId    TxId for ACH, UETR for RTGS
 * @param originalMessageId for a status report, the message being answered
 * @param status           for a status report, the reported status
 * @param reasonCode       for a rejection, why
 * @param debtorName       payer's name
 * @param debtorIban       payer's account
 * @param debtorAgentBic   payer's bank
 * @param creditorName     beneficiary's name
 * @param creditorIban     beneficiary's account, resolved against our books
 * @param creditorAgentBic beneficiary's bank
 * @param amount           settlement amount
 * @param currency         settlement currency
 * @param remittance       unstructured remittance information
 */
public record ParsedMessage(
        PacsMessageType messageType,
        String messageId,
        String endToEndId,
        String transactionId,
        String originalMessageId,
        TransactionStatusCode status,
        ReasonCode reasonCode,
        String debtorName,
        String debtorIban,
        String debtorAgentBic,
        String creditorName,
        String creditorIban,
        String creditorAgentBic,
        BigDecimal amount,
        String currency,
        String remittance
) {

    public boolean isCreditTransfer() {
        return messageType == PacsMessageType.PACS_008;
    }

    public boolean isStatusReport() {
        return messageType == PacsMessageType.PACS_002;
    }
}
