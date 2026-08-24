package com.hyperlocal.dto;

public record DashboardSummaryResponse(
        long totalUsers,
        long totalShops,
        long pendingShopApprovals,
        long activeOrders,
        long availableDeliveryPartners
) {}