package io.camunda.example.classic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionSearch;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.dto.PdfConnectorRequest;
import io.camunda.dto.PdfConnectorResult;
import io.camunda.example.operations.PdfConnectorProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

class PdfConnectorTest {

    private final DocumentFactory documentFactory = 
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    private final PdfConnectorProvider provider = new PdfConnectorProvider();

    @Test
    void shouldMergeTwoPdfs() throws IOException {
        // Create two test PDFs
        Document pdf1 = createTestPdf(3);
        Document pdf2 = createTestPdf(2);

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                List.of(pdf1, pdf2),
                "merged.pdf",
                false,
                "KEEP_ORIGINAL"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(5);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(2);
    }

    @Test
    void shouldSplitPdfByPage() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByPage splitRequest = 
            new PdfConnectorRequest.SplitByPage(
                pdf,
                1,
                "page-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(splitRequest)
            .build();

        PdfConnectorResult result = provider.splitByPage(splitRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isEqualTo(5);
        assertThat(splitResult.originalPages()).isEqualTo(5);
        assertThat(splitResult.splitMethod()).isEqualTo("BY_PAGE");
    }

    @Test
    void shouldSplitPdfByRange() throws IOException {
        Document pdf = createTestPdf(10);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "1-3,5-7,9-10",
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        PdfConnectorResult result = provider.splitByRange(rangeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isEqualTo(3);
        assertThat(splitResult.splitMethod()).isEqualTo("BY_RANGE");
    }

    @Test
    void shouldThrowExceptionForInvalidRange() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "1-10", // Invalid: document only has 5 pages
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        assertThrows(ConnectorException.class, () -> {
            provider.splitByRange(rangeRequest, context);
        });
    }

    @Test
    void shouldMergePdfsWithLargestPageSize() throws IOException {
        Document pdf1 = createTestPdf(2); // A4
        Document pdf2 = createTestPdfWithSize(2, PDRectangle.LETTER); // Letter size

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                List.of(pdf1, pdf2),
                "merged-largest.pdf",
                false,
                "USE_LARGEST"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(4);
    }

    @Test
    void shouldSplitPdfByPageMultiple() throws IOException {
        Document pdf = createTestPdf(6);

        PdfConnectorRequest.SplitByPage splitRequest = 
            new PdfConnectorRequest.SplitByPage(
                pdf,
                2, // 2 pages per file
                "chunk-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(splitRequest)
            .build();

        PdfConnectorResult result = provider.splitByPage(splitRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isEqualTo(3); // 6 pages / 2 pages per file
        assertThat(splitResult.originalPages()).isEqualTo(6);
    }

    private Document createTestPdf(int pageCount) throws IOException {
        return createTestPdfWithSize(pageCount, PDRectangle.A4);
    }

    private Document createTestPdfWithSize(int pageCount, PDRectangle pageSize) throws IOException {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pageCount; i++) {
            doc.addPage(new PDPage(pageSize));
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
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
