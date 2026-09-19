package com.hyperlocal.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ShopRequest {

    @NotBlank(message = "Name cannot be blank")
    private String name;

    @NotBlank(message = "Address cannot be blank")
    private String address;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9\\s\\-]{10,15}$", message = "Phone number must be between 10 and 15 digits (optional '+' prefix, spaces, or hyphens allowed)")
    private String phone;

    // Getters and Setters (or use Lombok @Data)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
