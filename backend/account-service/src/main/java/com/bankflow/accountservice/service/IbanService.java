package com.bankflow.accountservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
public class IbanService {

    private final String pspCode;
    private final String branchCode;

    public IbanService(
            @Value("${bankflow.account.psp-code:99}") String pspCode,
            @Value("${bankflow.account.branch-code:00}") String branchCode) {

        validateCode(pspCode, "PSP code");
        validateCode(branchCode, "Branch code");

        this.pspCode = pspCode;
        this.branchCode = branchCode;
    }

    public String generateAccountNumber() {

        String accountNumber;

        do {
            String clientNumber = generateTenDigitNumber();

            String bbanWithoutCheckDigits =
                    pspCode +
                            branchCode +
                            clientNumber;

            String checkDigits =
                    calculateMod97CheckDigits(bbanWithoutCheckDigits);

            accountNumber =
                    bbanWithoutCheckDigits +
                            checkDigits;

        } while (false);

        return accountNumber;
    }

    public String generateIban(String accountNumber) {

        if (accountNumber == null || !accountNumber.matches("\\d{16}")) {
            throw new IllegalArgumentException(
                    "Account number must contain exactly 16 digits"
            );
        }

        String ibanCheckDigits =
                calculateIbanCheckDigits(accountNumber);

        return "XK" + ibanCheckDigits + accountNumber;
    }

    private String generateTenDigitNumber() {

        long number = ThreadLocalRandom.current()
                .nextLong(0, 10_000_000_000L);

        return String.format("%010d", number);
    }

    private String calculateMod97CheckDigits(String value) {

        int remainder = mod97(value + "00");

        int checkDigits = 98 - remainder;

        return String.format("%02d", checkDigits);
    }

    private String calculateIbanCheckDigits(String bban) {

        String rearranged =
                bban + numericCountryCode("X") +
                        numericCountryCode("K") +
                        "00";

        int remainder = mod97(rearranged);

        int checkDigits = 98 - remainder;

        return String.format("%02d", checkDigits);
    }

    private int mod97(String numericValue) {

        int remainder = 0;

        for (char digit : numericValue.toCharArray()) {

            remainder =
                    (remainder * 10 + Character.digit(digit, 10))
                            % 97;
        }

        return remainder;
    }

    private String numericCountryCode(String character) {

        return switch (character) {
            case "X" -> "33";
            case "K" -> "20";
            default -> throw new IllegalArgumentException(
                    "Unsupported country character: " + character
            );
        };
    }

    private void validateCode(String code, String name) {

        if (code == null || !code.matches("\\d{2}")) {
            throw new IllegalArgumentException(
                    name + " must contain exactly 2 digits"
            );
        }
    }
}