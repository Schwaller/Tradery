package com.tradery.rendezvous;

public record EnrollRequest(String keycloakToken, String devicePublicKey, String deviceName) {}
