package com.hyperlocal.service;

import com.hyperlocal.dto.ShopRequest;
import com.hyperlocal.dto.ShopResponse;
import com.hyperlocal.entity.Role;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.repository.ShopRepository;
import org.springframework.stereotype.Service;
import com.hyperlocal.exception.AccessDeniedException;

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

    public ShopResponse createShop(ShopRequest request, User currentUser) {
        Shop shop = new Shop();
        shop.setName(request.getName());
        shop.setAddress(request.getAddress());
        shop.setPhone(request.getPhone());

        shop.setOwner(currentUser);
        Shop savedShop = shopRepository.save(shop);
        return mapToResponse(savedShop);
    }

    public ShopResponse updateShop(Long id, ShopRequest request, User currentUser) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + id));

        verifyShopOwnership(shop,currentUser);

        shop.setName(request.getName());
        shop.setAddress(request.getAddress());
        shop.setPhone(request.getPhone());

        Shop updatedShop = shopRepository.save(shop);
        return mapToResponse(updatedShop);
    }

    public void deleteShop(Long id, User currentUser) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + id));
        verifyShopOwnership(shop, currentUser);
        shopRepository.delete(shop);
    }

    // Helper method to keep code clean
    private ShopResponse mapToResponse(Shop shop) {
        Long ownerId = shop.getOwner() != null ? shop.getOwner().getId() : null;
        return new ShopResponse(shop.getId(), shop.getName(), shop.getAddress(), shop.getPhone(), ownerId);
    }

    private void verifyShopOwnership(Shop shop, User currentUser) {
        // Admins can bypass ownership rules
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }

        // Check if the current user's ID matches the shop owner's ID
        if (shop.getOwner() == null || !shop.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to modify this shop.");
        }
    }
}