package com.bankflow.transactionservice.pacs;

import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps a message before it is written.
 *
 * JPA did this with @PrePersist. Moving the archive to MongoDB took that
 * callback with it, and nothing would have complained: the message would
 * simply have been stored with no timestamp and no status, which the inspector
 * sorts by and the audit trail reports on.
 *
 * This is the document-store equivalent, and it runs for every save from every
 * caller, so the six places that write a message do not each have to remember.
 */
@Component
public class MessageDefaults implements BeforeConvertCallback<PacsMessage> {

    @Override
    public PacsMessage onBeforeConvert(PacsMessage message, String collection) {

        message.applyDefaults();

        return message;
    }
}
