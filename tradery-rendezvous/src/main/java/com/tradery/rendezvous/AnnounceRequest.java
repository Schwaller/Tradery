package com.tradery.rendezvous;

import java.util.List;

public record AnnounceRequest(String peerId, int port, List<String> documentIds) {}
