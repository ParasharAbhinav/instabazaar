package com.instabazaar.service;

import com.instabazaar.enums.Role;
import com.instabazaar.exception.DomainException;
import com.instabazaar.repository.ImageRepository;
import com.instabazaar.repository.SellerRepository;
import com.instabazaar.security.CurrentAccount;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class MediaService {
    public record Media(Resource resource, String contentType) {}
    private final ImageRepository images;
    private final SellerRepository sellers;
    private final CurrentAccount current;
    private final ViewMapper mapper;
    private final MediaStorage storage;

    public MediaService(ImageRepository images, SellerRepository sellers, CurrentAccount current, ViewMapper mapper, MediaStorage storage) {
        this.images = images; this.sellers = sellers; this.current = current; this.mapper = mapper; this.storage = storage;
    }
    public Media load(String key) {
        var user = current.optional();
        var image = images.findByStorageKey(key);
        if (image.isPresent()) {
            var product = image.get().getProduct();
            if (!mapper.visible(product) && (user == null || (user.getRole() != Role.ADMIN && !product.getSeller().getUser().getId().equals(user.getId())))) throw DomainException.missing();
            return new Media(storage.load(key), image.get().getContentType());
        }
        var seller = sellers.findByLogoKey(key).orElseThrow(DomainException::missing);
        if (!seller.getUser().getActive() && (user == null || user.getRole() != Role.ADMIN)) throw DomainException.missing();
        String type = key.endsWith(".jpg") ? "image/jpeg" : key.endsWith(".png") ? "image/png" : "image/webp";
        return new Media(storage.load(key), type);
    }
}
