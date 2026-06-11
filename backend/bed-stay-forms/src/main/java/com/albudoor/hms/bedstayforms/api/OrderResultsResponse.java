package com.albudoor.hms.bedstayforms.api;

import java.util.List;

/**
 * Results of one forwarded order viewed from its stay: the per-service findings text
 * plus the result documents (case attachments) streamable on the stay-scoped route.
 * Both lists are empty until the receiving department opens its case.
 */
public record OrderResultsResponse(List<ServiceFinding> services, List<StayDocumentDto> documents) {

    public record ServiceFinding(String serviceName, String findings) {}
}
