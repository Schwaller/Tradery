package com.tradery.sharing.tests;

import java.util.List;

public record AppendFactsRequest(List<FactEntry> facts) {

    public record FactEntry(String entityId, String attribute, String value, String source) {}
}
