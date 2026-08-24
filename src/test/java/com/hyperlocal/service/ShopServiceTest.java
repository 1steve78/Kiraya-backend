package com.hyperlocal.service;

import com.hyperlocal.dto.ShopRequest;
import com.hyperlocal.dto.ShopResponse;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.AccessDeniedException;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.model.Role;
import com.hyperlocal.model.ShopStatus;
import com.hyperlocal.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private ShopService shopService;

    private User owner;
    private Shop approvedShop;
    private Shop pendingShop;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setName("Shop Owner");
        owner.setEmail("owner@example.com");
        owner.setRole(Role.SHOP_OWNER);

        approvedShop = new Shop(1L, "Approved Grocery", "123 Main St", "+919876543210");
        approvedShop.setOwner(owner);
        approvedShop.setStatus(ShopStatus.APPROVED);

        pendingShop = new Shop(2L, "Pending Store", "456 Side St", "+919876543211");
        pendingShop.setOwner(owner);
        pendingShop.setStatus(ShopStatus.PENDING_APPROVAL);
    }

    @Test
    void testGetAllShops_OnlyReturnsApprovedShops() {
        when(shopRepository.findByStatus(ShopStatus.APPROVED)).thenReturn(List.of(approvedShop));

        List<ShopResponse> results = shopService.getAllShops();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Approved Grocery", results.get(0).getName());
        verify(shopRepository, times(1)).findByStatus(ShopStatus.APPROVED);
        verify(shopRepository, never()).findAll();
    }

    @Test
    void testGetShopById_ApprovedShop_Success() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(approvedShop));

        ShopResponse result = shopService.getShopById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Approved Grocery", result.getName());
    }

    @Test
    void testGetShopById_PendingShop_ThrowsShopNotFoundException() {
        when(shopRepository.findById(2L)).thenReturn(Optional.of(pendingShop));

        ShopNotFoundException exception = assertThrows(
                ShopNotFoundException.class,
                () -> shopService.getShopById(2L)
        );

        assertTrue(exception.getMessage().contains("currently unavailable") || exception.getMessage().contains("Shop not found"));
    }

    @Test
    void testGetShopById_SuspendedShop_ThrowsShopNotFoundException() {
        Shop suspendedShop = new Shop(3L, "Suspended Store", "789 Third St", "+919876543212");
        suspendedShop.setStatus(ShopStatus.SUSPENDED);

        when(shopRepository.findById(3L)).thenReturn(Optional.of(suspendedShop));

        assertThrows(ShopNotFoundException.class, () -> shopService.getShopById(3L));
    }

    @Test
    void testGetShopById_NonExistent_ThrowsShopNotFoundException() {
        when(shopRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ShopNotFoundException.class, () -> shopService.getShopById(99L));
    }

    @Test
    void testCreateShop_Success() {
        ShopRequest request = new ShopRequest();
        request.setName("New Store");
        request.setAddress("Street 1");
        request.setPhone("+919999999999");

        Shop saved = new Shop(5L, "New Store", "Street 1", "+919999999999");
        saved.setOwner(owner);
        saved.setStatus(ShopStatus.PENDING_APPROVAL);

        when(shopRepository.save(any(Shop.class))).thenReturn(saved);

        ShopResponse response = shopService.createShop(request, owner);

        assertNotNull(response);
        assertEquals(5L, response.getId());
        assertEquals("New Store", response.getName());
    }

    @Test
    void testUpdateShop_ByOwner_Success() {
        ShopRequest request = new ShopRequest();
        request.setName("Updated Store");
        request.setAddress("Updated Address");
        request.setPhone("+919999999999");

        when(shopRepository.findById(1L)).thenReturn(Optional.of(approvedShop));
        when(shopRepository.save(any(Shop.class))).thenReturn(approvedShop);

        ShopResponse response = shopService.updateShop(1L, request, owner);

        assertNotNull(response);
        assertEquals("Updated Store", response.getName());
    }

    @Test
    void testUpdateShop_ByOtherUser_ThrowsAccessDeniedException() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setRole(Role.SHOP_OWNER);

        ShopRequest request = new ShopRequest();
        when(shopRepository.findById(1L)).thenReturn(Optional.of(approvedShop));

        assertThrows(AccessDeniedException.class, () -> shopService.updateShop(1L, request, otherUser));
    }

    @Test
    void testDeleteShop_Success() {
        when(shopRepository.findById(1L)).thenReturn(Optional.of(approvedShop));

        shopService.deleteShop(1L, owner);

        verify(shopRepository, times(1)).delete(approvedShop);
    }
}
