package com.hyperlocal.service;

import com.hyperlocal.dto.ShopRequest;
import com.hyperlocal.dto.ShopResponse;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShopService {

    private final ShopRepository shopRepository;

    public ShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    public List<ShopResponse> getAllShops() {
        return shopRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ShopResponse getShopById(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + id));
        return mapToResponse(shop);
    }

    public ShopResponse createShop(ShopRequest request) {
        Shop shop = new Shop();
        shop.setName(request.getName());
        shop.setAddress(request.getAddress());
        shop.setPhone(request.getPhone());

        Shop savedShop = shopRepository.save(shop);
        return mapToResponse(savedShop);
    }

    public ShopResponse updateShop(Long id, ShopRequest request) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + id));

        shop.setName(request.getName());
        shop.setAddress(request.getAddress());
        shop.setPhone(request.getPhone());

        Shop updatedShop = shopRepository.save(shop);
        return mapToResponse(updatedShop);
    }

    public void deleteShop(Long id) {
        if (!shopRepository.existsById(id)) {
            throw new ShopNotFoundException("Shop not found with id: " + id);
        }
        shopRepository.deleteById(id);
    }

    // Helper method to keep code clean
    private ShopResponse mapToResponse(Shop shop) {
        return new ShopResponse(shop.getId(), shop.getName(), shop.getAddress(), shop.getPhone());
    }
}