package io.camunda.example.classic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
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
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Performance and load tests for PDF Connector.
 * Tests large file handling, memory efficiency, and operation speed.
 */
class PdfConnectorPerformanceTest {

    private final DocumentFactory documentFactory = 
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    private final PdfConnectorProvider provider = new PdfConnectorProvider();

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldMergeLargeNumberOfPdfs() throws IOException {
        // Create 50 PDFs with 2 pages each
        List<Document> pdfs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            pdfs.add(createTestPdfWithContent(2, "Document " + (i + 1)));
        }

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                pdfs,
                "large-merge.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(100);
        assertThat(mergeResult.sourceDocumentCount()).isEqualTo(50);
        
        System.out.println("Merged 50 PDFs (100 pages) in " + duration + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldSplitLargePdfByPage() throws IOException {
        // Create a large PDF with 100 pages
        Document largePdf = createTestPdfWithContent(100, "Large Document");

        PdfConnectorRequest.SplitByPage splitRequest = 
            new PdfConnectorRequest.SplitByPage(
                largePdf,
                1, // Split into individual pages
                "page-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(splitRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.splitByPage(splitRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isEqualTo(100);
        
        System.out.println("Split 100-page PDF into 100 files in " + duration + "ms");
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void shouldHandleVeryLargePdfMerge() throws IOException {
        // Create 10 large PDFs with 50 pages each
        List<Document> largePdfs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            largePdfs.add(createTestPdfWithContent(50, "Large Doc " + (i + 1)));
        }

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                largePdfs,
                "very-large-merge.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        PdfConnectorResult.MergeResult mergeResult = (PdfConnectorResult.MergeResult) result;
        assertThat(mergeResult.totalPages()).isEqualTo(500);
        
        System.out.println("Merged 10 large PDFs (500 pages total) in " + duration + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldSplitByMultipleRangesEfficiently() throws IOException {
        Document pdf = createTestPdfWithContent(100, "Test Document");

        // Create 10 ranges across the document
        String ranges = "1-10,11-20,21-30,31-40,41-50,51-60,61-70,71-80,81-90,91-100";
        
        PdfConnectorRequest.SplitByRange rangeRequest = 
            new PdfConnectorRequest.SplitByRange(
                pdf,
                ranges,
                "range-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(rangeRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.splitByRange(rangeRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isEqualTo(10);
        
        System.out.println("Split 100-page PDF by 10 ranges in " + duration + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldHandleSplitBySizeWithLargeDocument() throws IOException {
        // Create a document with substantial content
        Document largePdf = createTestPdfWithContent(50, "Large Content Document");

        PdfConnectorRequest.SplitBySize sizeRequest = 
            new PdfConnectorRequest.SplitBySize(
                largePdf,
                2, // 2 MB chunks
                "chunk-{index}.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(sizeRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.splitBySize(sizeRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        PdfConnectorResult.SplitResult splitResult = (PdfConnectorResult.SplitResult) result;
        assertThat(splitResult.totalFiles()).isGreaterThan(0);
        
        System.out.println("Split large PDF by size in " + duration + "ms, created " + 
                         splitResult.totalFiles() + " files");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void shouldHandleRepeatedMergeOperations() throws IOException {
        // Test that multiple operations don't cause memory leaks or slowdown
        Document pdf1 = createTestPdf(5);
        Document pdf2 = createTestPdf(5);

        for (int i = 0; i < 10; i++) {
            PdfConnectorRequest.MergePdfs mergeRequest = 
                new PdfConnectorRequest.MergePdfs(
                    List.of(pdf1, pdf2),
                    "merge-" + i + ".pdf"
                );

            OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(mergeRequest)
                .build();

            PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);
            assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        }
        
        System.out.println("Completed 10 repeated merge operations successfully");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void shouldHandleRepeatedSplitOperations() throws IOException {
        Document pdf = createTestPdf(20);

        for (int i = 0; i < 10; i++) {
            PdfConnectorRequest.SplitByPage splitRequest = 
                new PdfConnectorRequest.SplitByPage(
                    pdf,
                    2,
                    "split-" + i + "-{index}.pdf"
                );

            OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
                .variables(splitRequest)
                .build();

            PdfConnectorResult result = provider.splitByPage(splitRequest, context);
            assertThat(result).isInstanceOf(PdfConnectorResult.SplitResult.class);
        }
        
        System.out.println("Completed 10 repeated split operations successfully");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void shouldHandleMinimalPdfsEfficiently() throws IOException {
        // Test that small operations are still fast
        Document pdf1 = createTestPdf(1);
        Document pdf2 = createTestPdf(1);

        PdfConnectorRequest.MergePdfs mergeRequest = 
            new PdfConnectorRequest.MergePdfs(
                List.of(pdf1, pdf2),
                "minimal-merge.pdf"
            );

        OutboundConnectorContext context = OutboundConnectorContextBuilder.create()
            .variables(mergeRequest)
            .build();

        long startTime = System.currentTimeMillis();
        PdfConnectorResult result = provider.mergePdfs(mergeRequest, context);
        long duration = System.currentTimeMillis() - startTime;

        assertThat(duration).isLessThan(5000); // Should complete in under 5 seconds
        assertThat(result).isInstanceOf(PdfConnectorResult.MergeResult.class);
        
        System.out.println("Merged 2 minimal PDFs in " + duration + "ms");
    }

    private Document createTestPdf(int pageCount) throws IOException {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pageCount; i++) {
            doc.addPage(new PDPage(PDRectangle.A4));
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

    private Document createTestPdfWithContent(int pageCount, String title) throws IOException {
        PDDocument doc = new PDDocument();
        
        for (int i = 0; i < pageCount; i++) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            
            // Add text content to make pages more realistic in size
            try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText(title + " - Page " + (i + 1));
                contentStream.endText();
                
                // Add some additional content
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                contentStream.newLineAtOffset(50, 700);
                for (int j = 0; j < 30; j++) {
                    contentStream.showText("This is sample text line " + (j + 1) + " on page " + (i + 1));
                    contentStream.newLineAtOffset(0, -15);
                }
                contentStream.endText();
            }
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doc.save(outputStream);
        doc.close();

        return documentFactory.create(
            DocumentCreationRequest.from(outputStream.toByteArray())
                .fileName(title.replaceAll(" ", "-").toLowerCase() + ".pdf")
                .contentType("application/pdf")
                .build()
        );
    }
}
