package com.instabazaar.dto;

import com.instabazaar.util.Text;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SearchQuery(@Size(max = 160) String q, @Positive Long categoryId, @Positive Long sellerId,
                          @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal minPrice,
                          @DecimalMin("0") @DecimalMax("9999999999.99") BigDecimal maxPrice,
                          @Size(max = 50) String colour, @Size(max = 30) String size,
                          Boolean available, @Pattern(regexp = "relevance|price-asc|price-desc|newest") String sort,
                          @Min(0) @Max(100000) Integer page) {
    public SearchQuery {
        q = Text.clean(q); colour = Text.clean(colour); size = Text.clean(size);
        sort = Text.clean(sort).isEmpty() ? "relevance" : sort;
        page = page == null ? 0 : page;
    }
    public static SearchQuery empty() { return new SearchQuery("", null, null, null, null, "", "", null, "relevance", 0); }
}
