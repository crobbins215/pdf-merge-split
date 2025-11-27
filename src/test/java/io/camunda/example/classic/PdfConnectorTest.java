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
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.dto.PdfConnectorRequest;
import io.camunda.dto.PdfConnectorResult;
import io.camunda.example.operations.PdfConnectorProvider;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Collections;

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
                "merged.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;        assertThat(mergeResult.totalPages()).isEqualTo(5);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(2);
        assertThat(mergeResult.mergedDocument()).isNotNull();
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
    void shouldMergeMultiplePdfs() throws IOException {
        Document pdf1 = createTestPdf(2);
        Document pdf2 = createTestPdf(3);
        Document pdf3 = createTestPdf(1);

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                List.of(pdf1, pdf2, pdf3),
                "merged-multiple.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(6);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(3);
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

    @Test
    void shouldSplitPdfByBookmark() throws IOException {
        Document pdf = createTestPdfWithBookmarks(10);

        PdfConnectorRequest.SplitByBookmark bookmarkRequest = 
            new PdfConnectorRequest.SplitByBookmark(
                pdf,
                true,
                "section-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(bookmarkRequest)
            .build();

        PdfConnectorResult result = provider.splitByBookmark(bookmarkRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isGreaterThan(0);
        assertThat(splitResult.splitMethod()).isEqualTo("BY_BOOKMARK");
    }

    @Test
    void shouldThrowExceptionWhenSplitByBookmarkWithNoBookmarks() throws IOException {
        Document pdf = createTestPdf(5); // No bookmarks

        PdfConnectorRequest.SplitByBookmark bookmarkRequest = 
            new PdfConnectorRequest.SplitByBookmark(
                pdf,
                true,
                "section-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(bookmarkRequest)
            .build();

        assertThrows(ConnectorException.class, () -> {
            provider.splitByBookmark(bookmarkRequest, context);
        });
    }

    @Test
    void shouldSplitPdfBySize() throws IOException {
        Document pdf = createTestPdf(20); // Large PDF

        PdfConnectorRequest.SplitBySize sizeRequest = 
            new PdfConnectorRequest.SplitBySize(
                pdf,
                1, // 1 MB max
                "chunk-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(sizeRequest)
            .build();

        PdfConnectorResult result = provider.splitBySize(sizeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isGreaterThan(0);
        assertThat(splitResult.splitMethod()).isEqualTo("BY_SIZE");
    }

    @Test
    void shouldHandleEmptyDocumentList() throws IOException {
        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                Collections.emptyList(),
                "merged.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);
        
        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(0);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(0);
    }

    @Test
    void shouldHandleInvalidPageRange() throws IOException {
        Document pdf = createTestPdf(5);

        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                "invalid-range",
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
    void shouldHandleSinglePagePdf() throws IOException {
        Document pdf = createTestPdf(1);

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
        assertThat(splitResult.totalFiles()).isEqualTo(1);
    }

    @Test
    void shouldMergeSinglePdf() throws IOException {
        Document pdf = createTestPdf(3);

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                List.of(pdf),
                "single-merged.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(3);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(1);
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

    private Document createTestPdfWithBookmarks(int pageCount) throws IOException {
        PDDocument doc = new PDDocument();
        
        // Add pages
        for (int i = 0; i < pageCount; i++) {
            doc.addPage(new PDPage(PDRectangle.A4));
        }
        
        // Add bookmarks with page destinations
        PDDocumentOutline outline = new PDDocumentOutline();
        doc.getDocumentCatalog().setDocumentOutline(outline);
        
        PDOutlineItem bookmark1 = new PDOutlineItem();
        bookmark1.setTitle("Section 1");
        bookmark1.setDestination(doc.getPage(0)); // Link to first page
        outline.addLast(bookmark1);
        
        PDOutlineItem bookmark2 = new PDOutlineItem();
        bookmark2.setTitle("Section 2");
        bookmark2.setDestination(doc.getPage(Math.min(5, pageCount - 1))); // Link to middle page
        outline.addLast(bookmark2);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doc.save(outputStream);
        doc.close();

        return documentFactory.create(
            DocumentCreationRequest.from(outputStream.toByteArray())
                .fileName("test-bookmarks.pdf")
                .contentType("application/pdf")
                .build()
        );
    }
}
