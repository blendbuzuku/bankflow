package com.bankflow.transactionservice.pacs;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Small DOM helper for assembling ISO 20022 messages.
 *
 * Built as a document rather than concatenated strings so that escaping is
 * handled for us — a customer named {@code Smith & Sons} must not be able to
 * produce malformed XML, let alone inject elements.
 */
public final class XmlBuilder {

    /**
     * ISO 20022 date-times must carry a real UTC offset. The KIPS BAH pattern
     * for CreDt explicitly requires {@code (+|-)HH:MM}, which means the {@code Z}
     * that {@link java.time.Instant#toString()} produces is rejected.
     */
    private static final DateTimeFormatter OFFSET_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final Document document;

    private XmlBuilder(Document document) {
        this.document = document;
    }

    public static XmlBuilder create() {

        try {

            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);

            /*
             * These messages are assembled locally, but the same factory
             * settings are used when parsing inbound XML, so external entity
             * resolution stays off everywhere.
             */
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );

            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            return new XmlBuilder(
                    factory.newDocumentBuilder().newDocument()
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not create XML document builder",
                    exception
            );
        }
    }

    public Document document() {
        return document;
    }

    /** Creates a namespaced root element and attaches it. */
    public Element root(String namespace, String name) {

        Element element = document.createElementNS(namespace, name);
        document.appendChild(element);
        return element;
    }

    /** Creates a namespaced element without attaching it. */
    public Element element(String namespace, String name) {
        return document.createElementNS(namespace, name);
    }

    /** Appends an empty child element and returns it. */
    public Element child(Element parent, String name) {

        Element element =
                document.createElementNS(parent.getNamespaceURI(), name);

        parent.appendChild(element);
        return element;
    }

    /** Appends a child element carrying text. */
    public Element text(Element parent, String name, String value) {

        Element element = child(parent, name);
        element.setTextContent(value);
        return element;
    }

    /**
     * Appends a child only when the value is present, which keeps optional
     * ISO elements out of the message rather than emitting them empty.
     */
    public Element optional(Element parent, String name, String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return text(parent, name, value);
    }

    public static String formatDateTime(OffsetDateTime value) {
        return value.format(OFFSET_DATE_TIME);
    }

    /** Serialises the document, indented for readability in the UI. */
    public String toXml() {

        try {

            Transformer transformer =
                    TransformerFactory.newInstance().newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.setOutputProperty(
                    "{http://xml.apache.org/xslt}indent-amount",
                    "2"
            );

            StringWriter writer = new StringWriter();

            transformer.transform(
                    new DOMSource(document),
                    new StreamResult(writer)
            );

            return writer.toString();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialise XML document",
                    exception
            );
        }
    }
}
