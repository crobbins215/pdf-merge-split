package io.camunda.dto;

import io.camunda.connector.api.document.Document;
import java.util.List;

public sealed interface PdfConnectorResult {
    
    record MergeResult(
        Document mergedDocument,
        int totalPages,
        int sourceDocumentCount,
        long fileSizeBytes
    ) implements PdfConnectorResult {}
    
    record SplitResult(
        List<Document> splitDocuments,
        int totalFiles,
        int originalPages,
        String splitMethod
    ) implements PdfConnectorResult {}
}
