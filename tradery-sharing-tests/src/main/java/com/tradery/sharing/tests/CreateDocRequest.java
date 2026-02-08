package com.tradery.sharing.tests;

public record CreateDocRequest(
    String docId,
    String name,
    String governanceType,
    double votingQuorum
) {
    public CreateDocRequest(String docId, String name) {
        this(docId, name, "OPEN", 0.51);
    }
}
