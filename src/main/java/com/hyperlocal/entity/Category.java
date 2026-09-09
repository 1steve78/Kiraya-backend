package com.hyperlocal.entity;


import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;

    private  String name ;
    private  String description ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private  Shop shop;

    @OneToMany(mappedBy = "category" , cascade =  CascadeType.ALL , orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    public Category(){
    }

    public  Category(String name , String description , Shop shop){
        this.name = name ;
        this.description = description ;
        this.shop = shop;
    }

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
    public Shop getShop() {
        return shop;
    }
    public void setShop(Shop shop) {
        this.shop = shop;
    }
    public List<Product> getProducts() { return products ;}
    public void setProducts(List<Product> products) { this.products = products ; }

}
