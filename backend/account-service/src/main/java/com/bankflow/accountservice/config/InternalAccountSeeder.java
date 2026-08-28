package com.bankflow.accountservice.config;

import com.bankflow.accountservice.entity.Account;
import com.bankflow.accountservice.entity.AccountStatus;
import com.bankflow.accountservice.entity.AccountType;
import com.bankflow.accountservice.entity.BusinessClient;
import com.bankflow.accountservice.entity.Client;
import com.bankflow.accountservice.entity.ClientStatus;
import com.bankflow.accountservice.entity.Currency;
import com.bankflow.accountservice.repository.AccountRepository;
import com.bankflow.accountservice.repository.ClientRepository;
import com.bankflow.accountservice.service.IbanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Ensures the bank's own accounts exist.
 *
 * The bank is modelled as a {@link BusinessClient} rather than as a special
 * case, because a bank genuinely is a legal entity that holds accounts. That
 * keeps Account.client non-null and needs no schema change — internal accounts
 * are distinguished by their {@link AccountType}, not by a null owner.
 *
 * Idempotent: existing accounts are left untouched, so balances survive
 * restarts.
 *
 * Implemented as a component rather than a @Bean lambda so that Spring's
 * transaction proxy actually wraps {@link #run}; a @Transactional method
 * invoked from inside a lambda would silently run without a transaction.
 */
@Component
public class InternalAccountSeeder implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(InternalAccountSeeder.class);

    /**
     * Reserved user ID for the bank itself. Client.userId is unique and
     * non-null, and no real user will ever be issued a negative ID.
     */
    private static final Long BANK_USER_ID = -1L;

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final IbanService ibanService;

    private final String bankName;
    private final String bankEmail;
    private final String registrationNumber;

    public InternalAccountSeeder(
            ClientRepository clientRepository,
            AccountRepository accountRepository,
            IbanService ibanService,
            @Value("${bankflow.bank.name:BankFlow}") String bankName,
            @Value("${bankflow.bank.email:treasury@bankflow.local}") String bankEmail,
            @Value("${bankflow.bank.registration-number:XK-BANKFLOW-0001}")
            String registrationNumber) {

        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.ibanService = ibanService;
        this.bankName = bankName;
        this.bankEmail = bankEmail;
        this.registrationNumber = registrationNumber;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        Client bank = clientRepository.findByUserId(BANK_USER_ID)
                .orElseGet(this::createBankClient);

        int created = 0;

        /*
         * One of each per currency. They cannot be pooled: offsetting a EUR
         * obligation against a USD one needs an exchange rate and an FX
         * position, and a single mixed-currency balance would be a number in
         * no currency at all.
         */
        for (Currency currency : Currency.values()) {
            created += ensureAccount(bank, AccountType.SUSPENSE, currency);
            created += ensureAccount(bank, AccountType.INCOME, currency);
            created += ensureAccount(bank, AccountType.VAULT, currency);
            created += ensureAccount(bank, AccountType.SETTLEMENT, currency);
        }

        if (created > 0) {
            log.info("Seeded {} internal bank account(s)", created);
        }
    }

    private Client createBankClient() {

        BusinessClient bank = new BusinessClient();

        bank.setUserId(BANK_USER_ID);
        bank.setLegalName(bankName);
        bank.setRegistrationNumber(registrationNumber);
        bank.setIndustry("Banking");
        bank.setEmail(bankEmail);
        bank.setStatus(ClientStatus.ACTIVE.name());

        log.info("Creating bank client record for {}", bankName);

        return clientRepository.save(bank);
    }

    private int ensureAccount(
            Client bank,
            AccountType accountType,
            Currency currency) {

        boolean exists = accountRepository
                .findByAccountTypeAndCurrency(accountType, currency)
                .isPresent();

        if (exists) {
            return 0;
        }

        String accountNumber;

        do {
            accountNumber = ibanService.generateAccountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = new Account();

        account.setClient(bank);
        account.setAccountNumber(accountNumber);
        account.setIban(ibanService.generateIban(accountNumber));
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);

        accountRepository.save(account);

        return 1;
    }
}
