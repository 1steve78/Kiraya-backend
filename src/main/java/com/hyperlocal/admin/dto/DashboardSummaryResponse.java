package com.hyperlocal.admin.dto;

public record DashboardSummaryResponse(
        long totalUsers,
        long totalShops,
        long pendingShopApprovals,
        long activeOrders,
        long availableDeliveryPartners
) {}
