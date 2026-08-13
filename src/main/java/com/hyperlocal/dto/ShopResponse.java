package com.hyperlocal.dto;

public class ShopResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;

    // Constructor to easily map from Entity
    public ShopResponse(Long id, String name, String address, String phone) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
}