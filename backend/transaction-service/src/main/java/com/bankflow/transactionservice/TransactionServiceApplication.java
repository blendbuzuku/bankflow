package com.bankflow.transactionservice;

import com.bankflow.common.exception.GlobalExceptionHandler;
import com.bankflow.transactionservice.pacs.KipsProperties;
import com.bankflow.transactionservice.service.ApprovalProperties;
import com.bankflow.transactionservice.service.CustomerPaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties({
        KipsProperties.class,
        ApprovalProperties.class,
        CustomerPaymentProperties.class
})
@Import(GlobalExceptionHandler.class)
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                TransactionServiceApplication.class,
                args
        );
    }
}