package com.hyperlocal.dto;

import java.math.BigDecimal;

public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private Long shopId;
    private Long categoryId;

    // No-args constructor
    public ProductResponse() {
    }

    // All-args constructor
    public ProductResponse(Long id, String name, String description, BigDecimal price, Integer stockQuantity, Long shopId , Long categoryId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.shopId = shopId;
        this.categoryId = categoryId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public Long getCategoryId() { return categoryId;}

    public void setCategoryId(Long categoryId) {this.categoryId = categoryId;}
}