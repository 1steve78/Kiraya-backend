package com.hyperlocal.dto;

public class ShopResponse {
    private Long id;
    private String name;
    private String address;
    private String phone;
    private Long ownerId;

    // Constructor to easily map from Entity
    public ShopResponse(Long id, String name, String address, String phone , Long ownerId) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.ownerId = ownerId;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public Long getOwnerId() { return ownerId; }
}