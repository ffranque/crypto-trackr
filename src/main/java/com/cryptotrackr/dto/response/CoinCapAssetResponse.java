package com.cryptotrackr.dto.response;

public record CoinCapAssetResponse(Data data) {

    public record Data(String id, String symbol, String name, String priceUsd) {}
}
