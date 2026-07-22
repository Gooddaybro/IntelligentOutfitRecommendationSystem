package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductInput;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminProductWrite;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminSkuWrite;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchChangeRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Service boundary for admin catalog writes so SQL persistence, audits and search reindex requests stay transactional.
 */
@Service
public class AdminProductService {
    private static final String DEFAULT_OPERATOR = "admin";

    private final AdminProductMapper adminProductMapper;
    private final AdminAuditMapper adminAuditMapper;
    private final ProductSearchChangeRecorder productSearchChangeRecorder;

    public AdminProductService(
            AdminProductMapper adminProductMapper,
            AdminAuditMapper adminAuditMapper,
            ProductSearchChangeRecorder productSearchChangeRecorder) {
        this.adminProductMapper = adminProductMapper;
        this.adminAuditMapper = adminAuditMapper;
        this.productSearchChangeRecorder = productSearchChangeRecorder;
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> listProducts() {
        return adminProductMapper.findProducts().stream()
                .map(this::normalizeProduct)
                .toList();
    }

    @Transactional
    public AdminProductResponse createProduct(AdminProductInput request) {
        ProductInput normalized = normalizeProductInput(request, null);
        AdminProductWrite product = new AdminProductWrite(
                normalized.spuCode(),
                normalized.name(),
                normalized.categoryId(),
                normalized.description(),
                normalized.mainImageUrl(),
                normalized.dbStatus());
        adminProductMapper.insertProduct(product);
        Long spuId = product.getId();
        if (spuId == null) {
            throw new IllegalStateException("generated key is missing");
        }
        createOrUpdateDefaultSku(spuId, normalized);
        replaceStyleTags(spuId, normalized.styleTags());
        insertAudit("CREATE_PRODUCT", "SPU", String.valueOf(spuId), "SUCCESS", normalized.spuCode());
        // The outbox write shares the product transaction so a committed catalog write cannot lose reindex intent.
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional
    public AdminProductResponse updateProduct(Long spuId, AdminProductInput request) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        ProductInput normalized = normalizeProductInput(request, spuId);
        AdminProductWrite product = new AdminProductWrite(
                normalized.spuCode(),
                normalized.name(),
                normalized.categoryId(),
                normalized.description(),
                normalized.mainImageUrl(),
                normalized.dbStatus());
        product.setId(spuId);
        int updated = adminProductMapper.updateProduct(product);
        if (updated == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        createOrUpdateDefaultSku(spuId, normalized);
        replaceStyleTags(spuId, normalized.styleTags());
        insertAudit("UPDATE_PRODUCT", "SPU", String.valueOf(spuId), "SUCCESS", normalized.spuCode());
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional
    public AdminProductResponse changeProductStatus(Long spuId, AdminProductStatusRequest request) {
        String dbStatus = toDbStatus(request == null ? null : request.status());
        int updated = adminProductMapper.updateProductStatus(spuId, dbStatus);
        if (updated == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        adminProductMapper.updateSkuStatusBySpuId(spuId, toSkuStatus(dbStatus));
        insertAudit("CHANGE_PRODUCT_STATUS", "SPU", String.valueOf(spuId), "SUCCESS", toApiStatus(dbStatus));
        productSearchChangeRecorder.record(spuId);
        return requireProduct(spuId);
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listCategories() {
        return adminProductMapper.findCategories();
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long categoryId, AdminCategoryRequest request) {
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("category id must be positive");
        }
        if (request != null && categoryId.equals(request.parentId())) {
            throw new BadRequestException("category cannot be its own parent");
        }
        String existingName = adminProductMapper.findCategoryNameById(categoryId);
        if (existingName == null) {
            throw new ResourceNotFoundException("category not found");
        }
        String requestedName = request == null ? null : trimToNull(request.name());
        String updatedName = requestedName == null ? existingName : requestedName;
        List<Long> affectedSpuIds = existingName.equals(updatedName)
                ? List.of()
                : adminProductMapper.findProductIdsByCategoryId(categoryId);
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        adminProductMapper.updateCategory(
                categoryId,
                updatedName,
                enabled ? "active" : "inactive",
                request == null ? null : request.sortOrder());
        AdminCategoryResponse category = findCategory(categoryId);
        insertAudit(enabled ? "ENABLE_CATEGORY" : "DISABLE_CATEGORY", "CATEGORY", String.valueOf(categoryId),
                "SUCCESS", category.name());
        // Category names are denormalized into search documents, so only name changes fan out reindex events.
        affectedSpuIds.forEach(productSearchChangeRecorder::record);
        return category;
    }

    private AdminProductResponse requireProduct(Long spuId) {
        AdminProductResponse product = adminProductMapper.findProductById(spuId);
        if (product == null) {
            throw new ResourceNotFoundException("product not found");
        }
        return normalizeProduct(product);
    }

    private AdminProductResponse normalizeProduct(AdminProductResponse product) {
        product.setStatus(toApiStatus(product.getStatus()));
        product.setStyleTags(adminProductMapper.findProductStyleTags(product.getSpuId()));
        return product;
    }

    private ProductInput normalizeProductInput(AdminProductInput request, Long spuId) {
        if (request == null) {
            throw new BadRequestException("product payload is required");
        }
        String spuCode = normalizeRequiredText(request.spuCode(), "spuCode");
        String name = normalizeRequiredText(request.name(), "name");
        Long categoryId = request.categoryId();
        if (categoryId == null || categoryId <= 0) {
            throw new BadRequestException("categoryId must be positive");
        }
        if (spuId != null && adminProductMapper.countProductById(spuId) == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        String dbStatus = toDbStatus(request.status() == null ? "DRAFT" : request.status());
        BigDecimal minPrice = nonNegativeMoney(request.minPrice(), "minPrice");
        BigDecimal maxPrice = nonNegativeMoney(request.maxPrice() == null ? minPrice : request.maxPrice(), "maxPrice");
        if (maxPrice.compareTo(minPrice) < 0) {
            maxPrice = minPrice;
        }
        return new ProductInput(
                spuCode,
                name,
                categoryId,
                trimToNull(request.mainImageUrl()),
                minPrice,
                maxPrice,
                dbStatus,
                trimToNull(request.description()),
                request.styleTags() == null ? List.of() : request.styleTags().stream()
                        .map(this::trimToNull)
                        .filter(value -> value != null && !value.isBlank())
                        .toList()
        );
    }

    private void createOrUpdateDefaultSku(Long spuId, ProductInput input) {
        Long firstSkuId = adminProductMapper.findFirstSkuIdBySpuId(spuId);
        String skuStatus = toSkuStatus(input.dbStatus());
        if (firstSkuId == null) {
            Long colorId = adminProductMapper.findFirstColorId();
            Long sizeId = adminProductMapper.findFirstSizeId();
            if (colorId == null || sizeId == null) {
                throw new BadRequestException("default color or size is missing");
            }
            AdminSkuWrite sku = new AdminSkuWrite(
                    defaultSkuCode(input.spuCode()),
                    spuId,
                    colorId,
                    sizeId,
                    input.minPrice(),
                    input.maxPrice(),
                    skuStatus);
            adminProductMapper.insertDefaultSku(sku);
            adminProductMapper.insertInventoryIfAbsent(sku.getId());
            return;
        }
        AdminSkuWrite sku = new AdminSkuWrite(
                defaultSkuCode(input.spuCode()),
                spuId,
                null,
                null,
                input.minPrice(),
                input.maxPrice(),
                skuStatus);
        sku.setId(firstSkuId);
        adminProductMapper.updateDefaultSku(sku);
        adminProductMapper.updateAllSkuStatuses(spuId, skuStatus);
    }

    private void replaceStyleTags(Long spuId, List<String> styleTags) {
        adminProductMapper.deleteStyleTagsBySpuId(spuId);
        for (String tag : styleTags) {
            Long tagId = adminProductMapper.findStyleTagId(tag);
            if (tagId != null) {
                adminProductMapper.insertStyleTag(spuId, tagId);
            }
        }
    }

    private AdminCategoryResponse findCategory(Long categoryId) {
        AdminCategoryResponse category = adminProductMapper.findCategoryById(categoryId);
        if (category == null) {
            throw new ResourceNotFoundException("category not found");
        }
        return category;
    }

    private void insertAudit(String action, String targetType, String targetId, String result, String summary) {
        adminAuditMapper.insertAuditLog(new AdminAuditEntry(
                DEFAULT_OPERATOR,
                action,
                targetType,
                targetId,
                result,
                summary == null ? "" : summary
        ));
    }

    private String toDbStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ON_SALE" -> "on_sale";
            case "OFF_SHELF", "OFF_SALE" -> "off_sale";
            case "DRAFT" -> "draft";
            case "DELETED" -> "deleted";
            default -> throw new BadRequestException("unsupported product status");
        };
    }

    private String toApiStatus(String dbStatus) {
        if (dbStatus == null) {
            return null;
        }
        return switch (dbStatus.toLowerCase(Locale.ROOT)) {
            case "on_sale" -> "ON_SALE";
            case "off_sale", "off_shelf" -> "OFF_SHELF";
            case "draft" -> "DRAFT";
            case "deleted" -> "DELETED";
            default -> dbStatus.toUpperCase(Locale.ROOT);
        };
    }

    private String toSkuStatus(String dbProductStatus) {
        return "on_sale".equals(dbProductStatus) ? "on_sale" : "off_sale";
    }

    private String normalizeRequiredText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(field + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal nonNegativeMoney(BigDecimal value, String field) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(field + " must be non-negative");
        }
        return normalized;
    }

    private String defaultSkuCode(String spuCode) {
        String prefix = spuCode.length() > 52 ? spuCode.substring(0, 52) : spuCode;
        return prefix + "-DEFAULT";
    }

    /**
     * Normalized product payload used inside the admin service transaction.
     */
    private record ProductInput(
            String spuCode,
            String name,
            Long categoryId,
            String mainImageUrl,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String dbStatus,
            String description,
            List<String> styleTags
    ) {
    }
}
