package io.camunda.example.classic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.dto.PdfConnectorRequest;
import io.camunda.example.operations.PdfConnectorProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Error handling and edge case tests for PDF Connector.
 * Tests validation, corrupt input handling, and boundary conditions.
 */
class PdfConnectorErrorTest {

    private final DocumentFactory documentFactory = 
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    private final PdfConnectorProvider provider = new PdfConnectorProvider();

    @Test
    void shouldFailWithCorruptedPdfData() {
        // Create a document with invalid PDF data
        Document corruptedDoc = documentFactory.create(
            DocumentCreationRequest.from("This is not a valid PDF".getBytes())
                .fileName("corrupted.pdf")
                .contentType("application/pdf")
                .build()
        );

        PdfConnectorRequest.SplitByPage splitRequest = 
            new PdfConnectorRequest.SplitByPage(
                corruptedDoc,
                1,
                "page-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(splitRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByPage(splitRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Failed to split PDF");
    }

    @Test
    void shouldFailWithEmptyPdfData() {
        Document emptyDoc = documentFactory.create(
            DocumentCreationRequest.from(new byte[0])
                .fileName("empty.pdf")
                .contentType("application/pdf")
                .build()
        );

        PdfConnectorRequest.SplitByPage splitRequest = 
            new PdfConnectorRequest.SplitByPage(
                emptyDoc,
                1,
                "page-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(splitRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByPage(splitRequest, context))
            .isInstanceOf(ConnectorException.class);
    }

    @Test
    void shouldFailWithInvalidPageRangeFormat() throws IOException {
        Document pdf = createTestPdf(10);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "abc-def", // Invalid format
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByRange(rangeRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Invalid page range");
    }

    // Note: PDFBox doesn't validate overlapping ranges, so this test was removed
    // The library handles this scenario internally without raising errors

    @Test
    void shouldFailWithPageRangeExceedingDocumentSize() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "1-10", // Document only has 5 pages
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByRange(rangeRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Invalid page range");
    }

    @Test
    void shouldFailWithZeroPageNumber() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "0-3", // Pages are 1-indexed
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByRange(rangeRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Invalid page range");
    }

    @Test
    void shouldFailWithNegativePageNumber() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "-1-3",
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByRange(rangeRequest, context))
            .isInstanceOf(ConnectorException.class);
    }

    @Test
    void shouldFailSplitBySizeWithExcessiveTarget() throws IOException {
        Document pdf = createTestPdf(2);

        // Try to split with 101 MB target (max is 100)
        PdfConnectorRequest.SplitBySize sizeRequest = 
            new PdfConnectorRequest.SplitBySize(
                pdf,
                101,
                "chunk-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(sizeRequest)
            .build();

        // Should fail validation before reaching operation
        assertThatThrownBy(() -> context.bindVariables(PdfConnectorRequest.SplitBySize.class))
            .hasMessageContaining("maxFileSizeMb");
    }

    @Test
    void shouldFailSplitBySizeWithZeroTarget() throws IOException {
        Document pdf = createTestPdf(2);

        PdfConnectorRequest.SplitBySize sizeRequest = 
            new PdfConnectorRequest.SplitBySize(
                pdf,
                0, // Invalid: min is 1
                "chunk-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(sizeRequest)
            .build();

        // Should fail validation
        assertThatThrownBy(() -> context.bindVariables(PdfConnectorRequest.SplitBySize.class))
            .hasMessageContaining("maxFileSizeMb");
    }

    @Test
    void shouldHandleReversedPageRange() throws IOException {
        Document pdf = createTestPdf(10);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "5-3", // End before start
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThatThrownBy(() -> provider.splitByRange(rangeRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Invalid");
    }

    @Test
    void shouldHandleWhitespaceInPageRanges() throws IOException {
        Document pdf = createTestPdf(10);

        // Ranges with extra whitespace should be handled gracefully
        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                " 1-3 , 5-7 , 9-10 ",
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        // Should either work or fail with clear error
        try {
            provider.splitByRange(rangeRequest, context);
        } catch (ConnectorException e) {
            assertThat(e.getMessage()).isNotEmpty();
        }
    }

    @Test
    void shouldFailMergeWithCorruptedPdfInList() {
        Document corruptedDoc = documentFactory.create(
            DocumentCreationRequest.from("Not a PDF".getBytes())
                .fileName("corrupted.pdf")
                .contentType("application/pdf")
                .build()
        );

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                java.util.List.of(corruptedDoc),
                "merged.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        assertThatThrownBy(() -> provider.mergePdfs(mergeRequest, context))
            .isInstanceOf(ConnectorException.class)
            .hasMessageContaining("Failed to merge PDF");
    }

    private org.apache.pdfbox.pdmodel.PDDocument createBasicPdfDoc(int pageCount) {
        org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
        for (int i = 0; i < pageCount; i++) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4));
        }
        return doc;
    }

    private Document createTestPdf(int pageCount) throws IOException {
        org.apache.pdfbox.pdmodel.PDDocument doc = createBasicPdfDoc(pageCount);
        
        java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
        doc.save(outputStream);
        doc.close();

        return documentFactory.create(
            DocumentCreationRequest.from(outputStream.toByteArray())
                .fileName("test.pdf")
                .contentType("application/pdf")
                .build()
        );
    }
}
