package io.camunda.example.operations;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.dto.PdfConnectorRequest;
import io.camunda.dto.PdfConnectorResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core PDF manipulation operations using Apache PDFBox.
 * 
 * <p>This class provides the implementation for all PDF merge and split operations.
 * It handles document loading, manipulation, and conversion back to Camunda Document objects.
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>PDF merging with bookmark preservation and page size standardization</li>
 *   <li>Page-based splitting (by count, range, bookmark, or size)</li>
 *   <li>Robust error handling with ConnectorException</li>
 *   <li>Memory-efficient streaming for document creation</li>
 * </ul>
 * 
 * <p><b>Error Codes:</b>
 * <ul>
 *   <li>PDF_MERGE_ERROR - Failed to merge PDF documents</li>
 *   <li>PDF_SPLIT_ERROR - Failed to split PDF document</li>
 *   <li>NO_BOOKMARKS - Document contains no bookmarks for bookmark-based splitting</li>
 *   <li>INVALID_PAGE_RANGE - Invalid page range specification</li>
 * </ul>
 * 
 * @author Camunda PDF Merge & Split Connector
 * @version 0.1.0-SNAPSHOT
 * @see PdfConnectorProvider
 * @see <a href="https://pdfbox.apache.org/">Apache PDFBox</a>
 */
public class PdfOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfOperations.class);
    private static final long BYTES_PER_MB = 1024 * 1024;

    /**
     * Executes a PDF merge operation.
     * 
     * @param merge The merge request with documents and configuration
     * @param context The connector context for document factory access
     * @return MergeResult with merged document and statistics
     */
    public static PdfConnectorResult executeMerge(
        PdfConnectorRequest.MergePdfs merge,
        OutboundConnectorContext context
    ) {
        return mergePdfs(merge, context);
    }

    /**
     * Executes a PDF split by page operation.
     * 
     * @param split The split request with pages per file configuration
     * @param context The connector context for document factory access
     * @return SplitResult with split documents and statistics
     */
    public static PdfConnectorResult executeSplitByPage(
        PdfConnectorRequest.SplitByPage split,
        OutboundConnectorContext context
    ) {
        return splitByPage(split, context);
    }

    /**
     * Executes a PDF split by page range operation.
     * 
     * @param range The split request with page range specification
     * @param context The connector context for document factory access
     * @return SplitResult with split documents and statistics
     */
    public static PdfConnectorResult executeSplitByRange(
        PdfConnectorRequest.SplitByRange range,
        OutboundConnectorContext context
    ) {
        return splitByRange(range, context);
    }

    /**
     * Executes a PDF split by bookmark operation.
     * 
     * @param bookmark The split request with bookmark configuration
     * @param context The connector context for document factory access
     * @return SplitResult with split documents and statistics
     */
    public static PdfConnectorResult executeSplitByBookmark(
        PdfConnectorRequest.SplitByBookmark bookmark,
        OutboundConnectorContext context
    ) {
        return splitByBookmark(bookmark, context);
    }

    /**
     * Executes a PDF split by file size operation.
     * 
     * @param size The split request with target file size
     * @param context The connector context for document factory access
     * @return SplitResult with split documents and statistics
     */
    public static PdfConnectorResult executeSplitBySize(
        PdfConnectorRequest.SplitBySize size,
        OutboundConnectorContext context
    ) {
        return splitBySize(size, context);
    }

    /**
     * Merges multiple PDF documents into a single document.
     * 
     * <p>Supports:
     * <ul>
     *   <li>Bookmark preservation with document name prefixes</li>
     *   <li>Page size standardization (KEEP_ORIGINAL, USE_LARGEST, USE_FIRST, A4)</li>
     * </ul>
     * 
     * @param merge The merge configuration
     * @param context The connector context
     * @return MergeResult containing the merged document
     * @throws ConnectorException with code PDF_MERGE_ERROR if merge fails
     */
    private static PdfConnectorResult.MergeResult mergePdfs(
        PdfConnectorRequest.MergePdfs merge,
        OutboundConnectorContext context
    ) {
        try {
            LOGGER.info("Merging {} PDF documents", merge.documents().size());
            
            boolean preserveBookmarks = merge.preserveBookmarks() != null && merge.preserveBookmarks();
            String sizeStandard = merge.pageSizeStandardization() != null ? 
                merge.pageSizeStandardization() : "USE_LARGEST";

            PDDocument mergedDoc = new PDDocument();
            List<String> documentNames = new ArrayList<>();
            PDRectangle targetPageSize = null;
            int totalSourcePages = 0;

            // First pass: determine target page size if standardization is needed
            if (!sizeStandard.equals("KEEP_ORIGINAL")) {
                targetPageSize = determineTargetPageSize(merge.documents(), sizeStandard);
            }

            // Merge documents
            for (int i = 0; i < merge.documents().size(); i++) {
                Document doc = merge.documents().get(i);
                String docName = doc.metadata().getFileName();
                documentNames.add(docName != null ? docName : "Document" + (i + 1));
                
                try (PDDocument sourceDoc = Loader.loadPDF(doc.asByteArray())) {
                    totalSourcePages += sourceDoc.getNumberOfPages();
                    
                    // Copy pages
                    for (PDPage page : sourceDoc.getPages()) {
                        PDPage importedPage = mergedDoc.importPage(page);
                        
                        // Apply page size standardization if needed
                        if (targetPageSize != null && !sizeStandard.equals("KEEP_ORIGINAL")) {
                            importedPage.setMediaBox(targetPageSize);
                        }
                    }
                    
                    // Preserve bookmarks if requested
                    if (preserveBookmarks) {
                        copyBookmarks(sourceDoc, mergedDoc, docName, mergedDoc.getNumberOfPages() - sourceDoc.getNumberOfPages());
                    }
                }
            }

            // Save merged document
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mergedDoc.save(outputStream);
            mergedDoc.close();

            byte[] pdfBytes = outputStream.toByteArray();
            Document mergedDocument = context.create(
                DocumentCreationRequest.from(pdfBytes)
                    .fileName(merge.outputFilename())
                    .contentType("application/pdf")
                    .build()
            );

            LOGGER.info("Successfully merged {} documents into {} pages", 
                merge.documents().size(), totalSourcePages);

            return new PdfConnectorResult.MergeResult(
                mergedDocument,
                totalSourcePages,
                merge.documents().size(),
                pdfBytes.length
            );

        } catch (IOException e) {
            throw new ConnectorException("PDF_MERGE_ERROR", 
                "Failed to merge PDF documents: " + e.getMessage(), e);
        }
    }

    private static PdfConnectorResult.SplitResult splitByPage(
        PdfConnectorRequest.SplitByPage split,
        OutboundConnectorContext context
    ) {
        try {
            LOGGER.info("Splitting PDF by {} pages per file", split.pagesPerFile());

            try (PDDocument document = Loader.loadPDF(split.document().asByteArray())) {
                int totalPages = document.getNumberOfPages();
                
                Splitter splitter = new Splitter();
                splitter.setSplitAtPage(split.pagesPerFile());
                List<PDDocument> splitDocs = splitter.split(document);

                List<Document> outputDocuments = new ArrayList<>();
                
                for (int i = 0; i < splitDocs.size(); i++) {
                    PDDocument splitDoc = splitDocs.get(i);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    splitDoc.save(outputStream);
                    splitDoc.close();

                    String filename = split.outputPattern().replace("{index}", String.valueOf(i + 1));
                    
                    Document outputDoc = context.create(
                        DocumentCreationRequest.from(outputStream.toByteArray())
                            .fileName(filename)
                            .contentType("application/pdf")
                            .build()
                    );
                    
                    outputDocuments.add(outputDoc);
                }

                LOGGER.info("Split {} pages into {} files", totalPages, outputDocuments.size());

                return new PdfConnectorResult.SplitResult(
                    outputDocuments,
                    outputDocuments.size(),
                    totalPages,
                    "BY_PAGE"
                );
            }

        } catch (IOException e) {
            throw new ConnectorException("PDF_SPLIT_ERROR", 
                "Failed to split PDF by page: " + e.getMessage(), e);
        }
    }

    private static PdfConnectorResult.SplitResult splitByRange(
        PdfConnectorRequest.SplitByRange range,
        OutboundConnectorContext context
    ) {
        try {
            LOGGER.info("Splitting PDF by ranges: {}", range.pageRanges());

            try (PDDocument document = Loader.loadPDF(range.document().asByteArray())) {
                int totalPages = document.getNumberOfPages();
                List<PageRange> ranges = parsePageRanges(range.pageRanges(), totalPages);
                List<Document> outputDocuments = new ArrayList<>();

                for (int i = 0; i < ranges.size(); i++) {
                    PageRange pageRange = ranges.get(i);
                    PDDocument rangeDoc = new PDDocument();

                    for (int pageNum = pageRange.start; pageNum <= pageRange.end; pageNum++) {
                        PDPage page = document.getPage(pageNum - 1); // PDFBox is 0-indexed
                        rangeDoc.importPage(page);
                    }

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    rangeDoc.save(outputStream);
                    rangeDoc.close();

                    String filename = range.outputPattern()
                        .replace("{index}", String.valueOf(i + 1))
                        .replace("{start}", String.valueOf(pageRange.start))
                        .replace("{end}", String.valueOf(pageRange.end));

                    Document outputDoc = context.create(
                        DocumentCreationRequest.from(outputStream.toByteArray())
                            .fileName(filename)
                            .contentType("application/pdf")
                            .build()
                    );

                    outputDocuments.add(outputDoc);
                }

                LOGGER.info("Split {} pages into {} range-based files", totalPages, outputDocuments.size());

                return new PdfConnectorResult.SplitResult(
                    outputDocuments,
                    outputDocuments.size(),
                    totalPages,
                    "BY_RANGE"
                );
            }

        } catch (IOException e) {
            throw new ConnectorException("PDF_SPLIT_ERROR", 
                "Failed to split PDF by range: " + e.getMessage(), e);
        }
    }

    private static PdfConnectorResult.SplitResult splitByBookmark(
        PdfConnectorRequest.SplitByBookmark bookmark,
        OutboundConnectorContext context
    ) {
        try {
            LOGGER.info("Splitting PDF by bookmarks (top-level only: {})", bookmark.topLevelOnly());

            try (PDDocument document = Loader.loadPDF(bookmark.document().asByteArray())) {
                int totalPages = document.getNumberOfPages();
                PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();

                if (outline == null) {
                    throw new ConnectorException("NO_BOOKMARKS", 
                        "PDF document does not contain any bookmarks");
                }

                List<BookmarkSection> sections = extractBookmarkSections(
                    outline, 
                    document, 
                    bookmark.topLevelOnly()
                );

                if (sections.isEmpty()) {
                    throw new ConnectorException("NO_BOOKMARKS", 
                        "No valid bookmarks found in PDF document");
                }

                List<Document> outputDocuments = new ArrayList<>();

                for (int i = 0; i < sections.size(); i++) {
                    BookmarkSection section = sections.get(i);
                    PDDocument sectionDoc = new PDDocument();

                    for (int pageNum = section.startPage; pageNum <= section.endPage; pageNum++) {
                        if (pageNum < totalPages) {
                            PDPage page = document.getPage(pageNum);
                            sectionDoc.importPage(page);
                        }
                    }

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    sectionDoc.save(outputStream);
                    sectionDoc.close();

                    String sanitizedBookmark = sanitizeFilename(section.title);
                    String filename = bookmark.outputPattern()
                        .replace("{bookmark}", sanitizedBookmark)
                        .replace("{index}", String.valueOf(i + 1));

                    Document outputDoc = context.create(
                        DocumentCreationRequest.from(outputStream.toByteArray())
                            .fileName(filename)
                            .contentType("application/pdf")
                            .build()
                    );

                    outputDocuments.add(outputDoc);
                }

                LOGGER.info("Split PDF into {} bookmark-based files", outputDocuments.size());

                return new PdfConnectorResult.SplitResult(
                    outputDocuments,
                    outputDocuments.size(),
                    totalPages,
                    "BY_BOOKMARK"
                );
            }

        } catch (IOException e) {
            throw new ConnectorException("PDF_SPLIT_ERROR", 
                "Failed to split PDF by bookmarks: " + e.getMessage(), e);
        }
    }

    private static PdfConnectorResult.SplitResult splitBySize(
        PdfConnectorRequest.SplitBySize size,
        OutboundConnectorContext context
    ) {
        try {
            LOGGER.info("Splitting PDF by max size: {} MB", size.maxFileSizeMb());

            try (PDDocument document = Loader.loadPDF(size.document().asByteArray())) {
                int totalPages = document.getNumberOfPages();
                long maxSizeBytes = size.maxFileSizeMb() * BYTES_PER_MB;
                
                List<Document> outputDocuments = new ArrayList<>();
                PDDocument currentDoc = new PDDocument();
                int fileIndex = 1;
                int pagesInCurrentDoc = 0;

                for (int i = 0; i < totalPages; i++) {
                    PDPage page = document.getPage(i);
                    currentDoc.importPage(page);
                    pagesInCurrentDoc++;

                    // Check size by saving to memory
                    ByteArrayOutputStream testStream = new ByteArrayOutputStream();
                    currentDoc.save(testStream);
                    long currentSize = testStream.toByteArray().length;

                    // If we exceeded the limit and have more than 1 page, save without last page
                    if (currentSize > maxSizeBytes && pagesInCurrentDoc > 1) {
                        // Remove the last page we just added
                        currentDoc.removePage(pagesInCurrentDoc - 1);
                        
                        // Save current document
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        currentDoc.save(outputStream);
                        currentDoc.close();

                        String filename = size.outputPattern()
                            .replace("{index}", String.valueOf(fileIndex++));

                        Document outputDoc = context.create(
                            DocumentCreationRequest.from(outputStream.toByteArray())
                                .fileName(filename)
                                .contentType("application/pdf")
                                .build()
                        );

                        outputDocuments.add(outputDoc);

                        // Start new document with the page we couldn't fit
                        currentDoc = new PDDocument();
                        currentDoc.importPage(page);
                        pagesInCurrentDoc = 1;
                    }
                }

                // Save remaining pages
                if (pagesInCurrentDoc > 0) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    currentDoc.save(outputStream);
                    currentDoc.close();

                    String filename = size.outputPattern()
                        .replace("{index}", String.valueOf(fileIndex));

                    Document outputDoc = context.create(
                        DocumentCreationRequest.from(outputStream.toByteArray())
                            .fileName(filename)
                            .contentType("application/pdf")
                            .build()
                    );

                    outputDocuments.add(outputDoc);
                } else {
                    currentDoc.close();
                }

                LOGGER.info("Split {} pages into {} size-based files", totalPages, outputDocuments.size());

                return new PdfConnectorResult.SplitResult(
                    outputDocuments,
                    outputDocuments.size(),
                    totalPages,
                    "BY_SIZE"
                );
            }

        } catch (IOException e) {
            throw new ConnectorException("PDF_SPLIT_ERROR", 
                "Failed to split PDF by size: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private static PDRectangle determineTargetPageSize(List<Document> documents, String standard) {
        if (standard.equals("A4")) {
            return PDRectangle.A4;
        }

        try {
            PDRectangle largestSize = null;
            PDRectangle firstSize = null;
            double maxArea = 0;

            for (Document doc : documents) {
                try (PDDocument pdf = Loader.loadPDF(doc.asByteArray())) {
                    if (pdf.getNumberOfPages() > 0) {
                        PDPage firstPage = pdf.getPage(0);
                        PDRectangle pageSize = firstPage.getMediaBox();

                        if (firstSize == null) {
                            firstSize = pageSize;
                        }

                        double area = pageSize.getWidth() * pageSize.getHeight();
                        if (area > maxArea) {
                            maxArea = area;
                            largestSize = pageSize;
                        }
                    }
                }
            }

            return standard.equals("USE_FIRST") ? firstSize : largestSize;

        } catch (IOException e) {
            LOGGER.warn("Failed to determine page size, using A4", e);
            return PDRectangle.A4;
        }
    }

    private static void copyBookmarks(PDDocument source, PDDocument target, String prefix, int pageOffset) {
        try {
            PDDocumentOutline sourceOutline = source.getDocumentCatalog().getDocumentOutline();
            if (sourceOutline == null) {
                return;
            }

            PDDocumentOutline targetOutline = target.getDocumentCatalog().getDocumentOutline();
            if (targetOutline == null) {
                targetOutline = new PDDocumentOutline();
                target.getDocumentCatalog().setDocumentOutline(targetOutline);
            }

            for (PDOutlineItem item : sourceOutline.children()) {
                PDOutlineItem mapped = mapOutlineItem(item, source, target, pageOffset, prefix);
                if (mapped != null) {
                    targetOutline.addLast(mapped);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to copy bookmarks from {}: {}", prefix, e.getMessage());
        }
    }

    private static PDOutlineItem mapOutlineItem(PDOutlineItem item, PDDocument source, PDDocument target, int pageOffset, String prefix) {
        try {
            PDOutlineItem newItem = new PDOutlineItem();
            String title = item.getTitle() != null ? item.getTitle() : "";
            newItem.setTitle(prefix != null && !prefix.isEmpty() ? prefix + " - " + title : title);

            Integer srcIndex = resolveBookmarkPageIndex(item, source);
            if (srcIndex != null && srcIndex >= 0 && srcIndex < source.getNumberOfPages()) {
                int targetIndex = pageOffset + srcIndex;
                if (targetIndex >= 0 && targetIndex < target.getNumberOfPages()) {
                    org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination dest =
                        new org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination();
                    dest.setPage(target.getPage(targetIndex));
                    newItem.setDestination(dest);
                }
            }

            // Recurse children
            PDOutlineItem child = item.getFirstChild();
            while (child != null) {
                PDOutlineItem mappedChild = mapOutlineItem(child, source, target, pageOffset, prefix);
                if (mappedChild != null) {
                    newItem.addLast(mappedChild);
                }
                child = child.getNextSibling();
            }
            return newItem;
        } catch (Exception e) {
            LOGGER.warn("Failed to map outline item '{}': {}", item.getTitle(), e.getMessage());
            return null;
        }
    }

    private static Integer resolveBookmarkPageIndex(PDOutlineItem item, PDDocument doc) {
        try {
            var dest = item.getDestination();
            if (dest instanceof org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination pd) {
                int idx = pd.retrievePageNumber();
                if (idx >= 0) return idx;
                if (pd.getPage() != null) return indexOfPage(doc, pd.getPage());
            }
            var action = item.getAction();
            if (action instanceof org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo goTo) {
                var d = goTo.getDestination();
                if (d instanceof org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination pd) {
                    int idx = pd.retrievePageNumber();
                    if (idx >= 0) return idx;
                    if (pd.getPage() != null) return indexOfPage(doc, pd.getPage());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int indexOfPage(PDDocument doc, PDPage page) {
        int i = 0;
        for (PDPage p : doc.getPages()) {
            if (p.getCOSObject() == page.getCOSObject()) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static List<PageRange> parsePageRanges(String rangesStr, int totalPages) {
        List<PageRange> ranges = new ArrayList<>();
        Pattern rangePattern = Pattern.compile("(\\d+)(?:-(\\d+))?");
        
        String[] parts = rangesStr.split(",");
        for (String part : parts) {
            part = part.trim();
            Matcher matcher = rangePattern.matcher(part);
            
            if (matcher.matches()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : start;
                
                if (start < 1 || end > totalPages || start > end) {
                    throw new ConnectorException("INVALID_PAGE_RANGE", 
                        String.format("Invalid page range: %s (document has %d pages)", part, totalPages));
                }
                
                ranges.add(new PageRange(start, end));
            } else {
                throw new ConnectorException("INVALID_PAGE_RANGE", 
                    "Invalid page range format: " + part);
            }
        }
        
        return ranges;
    }

    private static List<BookmarkSection> extractBookmarkSections(
        PDDocumentOutline outline,
        PDDocument document,
        boolean topLevelOnly
    ) {
        List<BookmarkSection> sections = new ArrayList<>();
        int totalPages = document.getNumberOfPages();
        
        PDOutlineItem current = outline.getFirstChild();
        while (current != null) {
            try {
                Integer startPage = resolveBookmarkPageIndex(current, document);
                if (startPage == null) {
                    current = current.getNextSibling();
                    continue;
                }
                PDOutlineItem next = current.getNextSibling();
                Integer nextStart = next != null ? resolveBookmarkPageIndex(next, document) : null;
                int endPage = nextStart != null ? nextStart - 1 : totalPages - 1;
                
                sections.add(new BookmarkSection(current.getTitle(), startPage, endPage));
            } catch (Exception e) {
                LOGGER.warn("Failed to process bookmark: {}", current.getTitle(), e);
            }
            
            current = current.getNextSibling();
        }
        
        return sections;
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    // Helper classes
    private record PageRange(int start, int end) {}
    private record BookmarkSection(String title, int startPage, int endPage) {}
}
