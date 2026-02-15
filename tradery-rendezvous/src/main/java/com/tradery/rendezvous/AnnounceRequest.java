package com.tradery.rendezvous;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnounceRequest(String peerId, int port, List<String> documentIds, String ipv6Address) {}
