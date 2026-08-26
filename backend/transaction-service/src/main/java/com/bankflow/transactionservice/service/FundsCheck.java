package com.bankflow.transactionservice.service;

import com.bankflow.common.exception.BusinessException;
import com.bankflow.transactionservice.client.AccountClient;
import com.bankflow.transactionservice.dto.AccountResponse;
import com.bankflow.transactionservice.entity.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Confirms the debtor can still cover a payment.
 *
 * Checking once at creation is not enough. A payment held for approval can wait
 * minutes or days, and the account keeps moving while it waits — other payments
 * settle, direct debits land. The balance that justified the instruction may be
 * gone by the time someone releases it, so the check is repeated at the moment
 * of booking, which is the only moment it is actually true of.
 *
 * The charge counts: a customer must be able to cover the amount *and* the fee,
 * or the payment is not affordable.
 */
@Service
public class FundsCheck {

    private final AccountClient accountClient;

    public FundsCheck(AccountClient accountClient) {
        this.accountClient = accountClient;
    }

    /**
     * @throws BusinessException when the account is gone, no longer active, or
     *         no longer holds enough
     */
    public void assertCanCover(Transaction transaction) {

        Long debtorAccountId = transaction.getSourceAccountId();

        if (debtorAccountId == null) {
            /*
             * An inbound payment has no account of ours to debit; the funds
             * arrive from the scheme.
             */
            return;
        }

        AccountResponse debtor = accountClient.getAccount(debtorAccountId);

        if (debtor == null) {
            throw new BusinessException(
                    "Debtor account " + debtorAccountId + " no longer exists"
            );
        }

        if (!"ACTIVE".equals(debtor.status())) {
            throw new BusinessException(
                    "Debtor account is %s and cannot be debited".formatted(
                            debtor.status()
                    )
            );
        }

        BigDecimal required = totalDebitFor(transaction);

        if (debtor.balance().compareTo(required) < 0) {

            throw new BusinessException(
                    ("Insufficient funds: %s %s required including charges, "
                            + "available %s").formatted(
                            required,
                            transaction.getCurrency(),
                            debtor.balance()
                    )
            );
        }
    }

    /**
     * Amount plus whatever share of the charge the debtor bears.
     */
    private BigDecimal totalDebitFor(Transaction transaction) {

        BigDecimal debtorFee =
                transaction.getDebtorFeeAmount() != null
                        ? transaction.getDebtorFeeAmount()
                        : BigDecimal.ZERO;

        return transaction.getAmount().add(debtorFee);
    }
}
