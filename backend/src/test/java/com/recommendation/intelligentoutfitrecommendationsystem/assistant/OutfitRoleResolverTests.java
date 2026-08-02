package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.OutfitRoleResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.OutfitRoleValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutfitRoleResolverTests {

    private final OutfitRoleResolver resolver = new OutfitRoleResolver();
    private final OutfitRoleValidator validator = new OutfitRoleValidator(resolver);

    @Test
    void mapsCatalogCategoriesToStableOutfitRoles() {
        assertThat(resolver.resolve("衬衫")).isEqualTo("TOP");
        assertThat(resolver.resolve("休闲裤")).isEqualTo("BOTTOM");
        assertThat(resolver.resolve("外套")).isEqualTo("OUTER");
        assertThat(resolver.resolve("休闲鞋")).isEqualTo("SHOES");
        assertThat(resolver.resolve("帽子")).isEqualTo("ACCESSORY");
        assertThat(resolver.resolve("其他")).isEqualTo("OTHER");
    }

    @Test
    void keepsOnlyCompatibleCanonicalProposals() {
        assertThat(validator.validate("衬衫", "TOP")).isEqualTo("TOP");
        assertThat(validator.validate("衬衫", "BOTTOM")).isEqualTo("TOP");
    }

    @Test
    void safelyMapsNullInvalidAndUnknownInputs() {
        assertThat(validator.validate("休闲裤", null)).isEqualTo("BOTTOM");
        assertThat(validator.validate("休闲鞋", "NOT_A_ROLE")).isEqualTo("SHOES");
        assertThat(validator.validate("未知品类", "TOP")).isEqualTo("OTHER");
        assertThat(validator.validate(null, null)).isEqualTo("OTHER");
    }
}
