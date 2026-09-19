package com.hyperlocal.catalog.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.auth.security.JwtService;
import com.hyperlocal.catalog.dto.ShopCreatedResponse;
import com.hyperlocal.catalog.dto.ShopRequest;
import com.hyperlocal.catalog.dto.ShopResponse;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.enums.ShopStatus;
import com.hyperlocal.catalog.exception.ShopNotFoundException;
import com.hyperlocal.catalog.repository.ShopRepository;
import com.hyperlocal.common.exception.AccessDeniedException;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopService {

    private final ShopRepository shopRepository;
    private final JwtService jwtService;

    public ShopService(ShopRepository shopRepository, JwtService jwtService) {
        this.shopRepository = shopRepository;
        this.jwtService = jwtService;
    }

    public List<ShopResponse> getAllShops() {
        return shopRepository.findByStatus(ShopStatus.APPROVED).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ShopResponse getShopById(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + id));

        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ShopNotFoundException("Shop not found or currently unavailable");
        }

        return mapToResponse(shop);
    }

    public ShopCreatedResponse createShop(ShopRequest request, User currentUser) {
        Shop shop = new Shop();
        shop.setName(request.getName());
        shop.setAddress(request.getAddress());
        shop.setPhone(request.getPhone());

        shop.setOwner(currentUser);
        Shop savedShop = shopRepository.save(shop);
        ShopResponse shopResponse = mapToResponse(savedShop);

        // Re-issue a fresh token that now contains the new shopId
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", currentUser.getRole().name());
        extraClaims.put("name", currentUser.getName());
        extraClaims.put("shopId", savedShop.getId());
        String freshToken = jwtService.generateToken(extraClaims, currentUser);

        return new ShopCreatedResponse(freshToken, shopResponse);
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
