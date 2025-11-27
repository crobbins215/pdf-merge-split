package io.camunda.example.operations;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.dto.PdfConnectorRequest;
import io.camunda.dto.PdfConnectorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PDF Merge & Split Connector Provider.
 * 
 * <p>This connector provides five PDF document manipulation operations using the Camunda Operations API pattern:
 * <ul>
 *   <li><b>Merge PDFs</b> - Combine multiple PDF documents into one, with bookmark preservation and page size standardization</li>
 *   <li><b>Split by Page</b> - Split a PDF into multiple files with specified pages per file</li>
 *   <li><b>Split by Range</b> - Split a PDF based on page range specifications (e.g., "1-3,5-7")</li>
 *   <li><b>Split by Bookmark</b> - Split a PDF into separate files based on document bookmarks</li>
 *   <li><b>Split by Size</b> - Split a PDF into files not exceeding a target file size</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * // In BPMN, configure service task with element template
 * // Select operation: "Merge PDFs"
 * // Configure inputs:
 * {
 *   "documents": [doc1, doc2],
 *   "outputFilename": "merged.pdf",
 *   "preserveBookmarks": true,
 *   "pageSizeStandardization": "USE_LARGEST"
 * }
 * </pre>
 * 
 * <p><b>Technology:</b>
 * <ul>
 *   <li>Apache PDFBox 3.0.3 for PDF processing</li>
 *   <li>Camunda Connector SDK 8.8.3</li>
 *   <li>Java 21</li>
 * </ul>
 * 
 * @author Camunda PDF Merge & Split Connector
 * @version 1.3.0
 * @see PdfOperations
 * @see PdfConnectorRequest
 * @see PdfConnectorResult
 */
@OutboundConnector(
    name = "PDF_MERGE_SPLIT",
    type = "io.camunda:pdf-connector:1")
@ElementTemplate(
    id = "io.camunda.connector.PdfMergeSplit.v1",
    name = "PDF Merge & Split Connector",
    version = 1,
    description = "Split and merge PDF documents with support for pages, ranges, bookmarks, and size-based operations",
    icon = "icon.svg",
    documentationRef = "https://github.com/crobbins215/pdf-merge-split/blob/main/README.md"
)
public class PdfConnectorProvider implements OutboundConnectorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfConnectorProvider.class);

    /**
     * Merges multiple PDF documents into a single file.
     * 
     * <p>This operation combines the provided PDF documents in order, preserving the original
     * page sizes and content of each document. The merge is performed using PDFBox's
     * PDFMergerUtility for reliable content preservation.
     * 
     * @param request The merge operation request containing documents and output filename
     * @param context The outbound connector context for document factory access
     * @return MergeResult containing the merged document and statistics
     * @throws io.camunda.connector.api.error.ConnectorException if merge fails
     */
    @Operation(id = "mergePdfs", name = "Merge PDFs")
    public PdfConnectorResult mergePdfs(
        @Variable PdfConnectorRequest.MergePdfs request,
        OutboundConnectorContext context
    ) {
        LOGGER.info("Executing PDF merge operation");
        return PdfOperations.executeMerge(request, context);
    }

    /**
     * Splits a PDF document into multiple files based on pages per file.
     * 
     * <p>Example: pagesPerFile=1 creates one file per page, pagesPerFile=5 creates files with 5 pages each.
     * 
     * @param request The split operation request specifying pages per file and naming pattern
     * @param context The outbound connector context for document factory access
     * @return SplitResult containing the split documents and statistics
     * @throws io.camunda.connector.api.error.ConnectorException if split fails
     */
    @Operation(id = "splitByPage", name = "Split by Page")
    public PdfConnectorResult splitByPage(
        @Variable PdfConnectorRequest.SplitByPage request,
        OutboundConnectorContext context
    ) {
        LOGGER.info("Executing PDF split by page operation");
        return PdfOperations.executeSplitByPage(request, context);
    }

    /**
     * Splits a PDF document based on specified page ranges.
     * 
     * <p>Page ranges are specified as comma-separated ranges, e.g., "1-3,5-7,10-15".
     * Pages are 1-indexed. Overlapping ranges will result in an error.
     * 
     * @param request The split operation request with page range specification
     * @param context The outbound connector context for document factory access
     * @return SplitResult containing the split documents and statistics
     * @throws io.camunda.connector.api.error.ConnectorException if ranges are invalid or split fails
     */
    @Operation(id = "splitByRange", name = "Split by Range")
    public PdfConnectorResult splitByRange(
        @Variable PdfConnectorRequest.SplitByRange request,
        OutboundConnectorContext context
    ) {
        LOGGER.info("Executing PDF split by range operation");
        return PdfOperations.executeSplitByRange(request, context);
    }

    /**
     * Splits a PDF document into separate files based on document bookmarks.
     * 
     * <p>By default, only top-level bookmarks are used for splitting. Set topLevelOnly=false
     * to split on all bookmark levels (may result in many small files).
     * 
     * @param request The split operation request with bookmark configuration
     * @param context The outbound connector context for document factory access
     * @return SplitResult containing the split documents and statistics
     * @throws io.camunda.connector.api.error.ConnectorException if document has no bookmarks or split fails
     */
    @Operation(id = "splitByBookmark", name = "Split by Bookmark")
    public PdfConnectorResult splitByBookmark(
        @Variable PdfConnectorRequest.SplitByBookmark request,
        OutboundConnectorContext context
    ) {
        LOGGER.info("Executing PDF split by bookmark operation");
        return PdfOperations.executeSplitByBookmark(request, context);
    }

    /**
     * Splits a PDF document into multiple files targeting a maximum file size.
     * 
     * <p>Pages are added iteratively until adding another page would exceed the target size.
     * The actual file size may vary as PDF compression is page-dependent. Target size is in megabytes (1-100 MB).
     * 
     * @param request The split operation request with target file size
     * @param context The outbound connector context for document factory access
     * @return SplitResult containing the split documents and statistics
     * @throws io.camunda.connector.api.error.ConnectorException if split fails
     */
    @Operation(id = "splitBySize", name = "Split by Size")
    public PdfConnectorResult splitBySize(
        @Variable PdfConnectorRequest.SplitBySize request,
        OutboundConnectorContext context
    ) {
        LOGGER.info("Executing PDF split by size operation");
        return PdfOperations.executeSplitBySize(request, context);
    }
}
