package com.hyperlocal.catalog.dto;

public class ShopCreatedResponse {

    private String token;
    private ShopResponse shop;

    public ShopCreatedResponse(String token, ShopResponse shop) {
        this.token = token;
        this.shop = shop;
    }

    public String getToken() { return token; }
    public ShopResponse getShop() { return shop; }
}
