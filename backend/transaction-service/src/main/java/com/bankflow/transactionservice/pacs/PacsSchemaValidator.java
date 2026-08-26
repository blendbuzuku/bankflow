package com.bankflow.transactionservice.pacs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates generated messages against the KIPS schemas.
 *
 * These are the operator's own restricted schemas, not the generic ISO
 * publications, so passing here means the message satisfies the rules KIPS
 * actually enforces — ACH permitting only SLEV, names capped at 70 characters,
 * and so on.
 *
 * Compiled schemas are cached: parsing a schema is expensive and they never
 * change at runtime. {@link Schema} is thread-safe; {@link Validator} is not,
 * so a fresh validator is taken per call.
 */
@Component
public class PacsSchemaValidator {

    private static final Logger log =
            LoggerFactory.getLogger(PacsSchemaValidator.class);

    private static final String BASE = "schema/kips/";

    private final Map<String, Schema> cache = new ConcurrentHashMap<>();

    /**
     * @param rail        "ach" or "rtgs"
     * @param messageType which schema to validate against
     * @param xml         the document to check
     */
    public ValidationResult validate(
            String rail,
            PacsMessageType messageType,
            String xml) {

        return validateAgainst(
                BASE + rail + "/" + messageType.getIdentifier() + ".xsd",
                xml
        );
    }

    /**
     * Validates a Business Application Header. head.001 ships only with the
     * RTGS schema set, which is also the only rail that uses it.
     */
    public ValidationResult validateHeader(String xml) {
        return validateAgainst(BASE + "rtgs/head.001.001.02.xsd", xml);
    }

    private ValidationResult validateAgainst(String schemaPath, String xml) {

        try {

            Schema schema = cache.computeIfAbsent(
                    schemaPath,
                    this::loadSchema
            );

            Validator validator = schema.newValidator();

            List<String> errors = new ArrayList<>();
            validator.setErrorHandler(collectingHandler(errors));

            validator.validate(
                    new StreamSource(
                            new ByteArrayInputStream(
                                    xml.getBytes(StandardCharsets.UTF_8)
                            )
                    )
            );

            return errors.isEmpty()
                    ? ValidationResult.ok()
                    : ValidationResult.failed(errors);

        } catch (SAXException exception) {

            return ValidationResult.failed(
                    List.of(exception.getMessage())
            );

        } catch (Exception exception) {

            log.error(
                    "Could not validate against {}: {}",
                    schemaPath,
                    exception.getMessage(),
                    exception
            );

            return ValidationResult.failed(
                    List.of("Validation could not run: " + exception.getMessage())
            );
        }
    }

    private Schema loadSchema(String path) {

        try (InputStream stream = new ClassPathResource(path).getInputStream()) {

            SchemaFactory factory = SchemaFactory.newInstance(
                    XMLConstants.W3C_XML_SCHEMA_NS_URI
            );

            /*
             * The schemas are local and trusted, but disabling external access
             * keeps a malformed import from reaching the network.
             */
            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );

            factory.setProperty(
                    XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                    ""
            );

            return factory.newSchema(new StreamSource(stream));

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load schema " + path,
                    exception
            );
        }
    }

    /**
     * Collects every problem instead of stopping at the first, so a message
     * with several faults can be fixed in one pass.
     */
    private ErrorHandler collectingHandler(List<String> errors) {

        return new ErrorHandler() {

            @Override
            public void warning(SAXParseException exception) {
                // Warnings do not make a message unacceptable.
            }

            @Override
            public void error(SAXParseException exception) {
                errors.add(describe(exception));
            }

            @Override
            public void fatalError(SAXParseException exception) {
                errors.add(describe(exception));
            }

            private String describe(SAXParseException exception) {
                return "line %d: %s".formatted(
                        exception.getLineNumber(),
                        exception.getMessage()
                );
            }
        };
    }

    public record ValidationResult(boolean valid, List<String> errors) {

        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }

        public String describe() {
            return String.join("; ", errors);
        }
    }
}
