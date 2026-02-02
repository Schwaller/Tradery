package com.tradery.core.model;

public record MarkPriceUpdate(long timestamp, double markPrice, double indexPrice,
                              double premium, double fundingRate, long nextFundingTime) {}
