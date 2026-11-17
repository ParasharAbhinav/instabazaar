package com.instabazaar.storefront.dto;

import java.util.UUID;

public record StoreWrite(
    String storeName,
    String description,
    UUID logoAssetId,
    String publicEmail,
    String publicPhone,
    String whatsappNumber,
    String instagramUrl,
    String facebookUrl,
    Long version) {}
