package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.OutfitRoleResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutfitRoleResolverTests {

    private final OutfitRoleResolver resolver = new OutfitRoleResolver();

    @Test
    void mapsCatalogCategoriesToStableOutfitRoles() {
        assertThat(resolver.resolve("衬衫")).isEqualTo("TOP");
        assertThat(resolver.resolve("休闲裤")).isEqualTo("BOTTOM");
        assertThat(resolver.resolve("外套")).isEqualTo("OUTER");
        assertThat(resolver.resolve("休闲鞋")).isEqualTo("SHOES");
        assertThat(resolver.resolve("帽子")).isEqualTo("ACCESSORY");
        assertThat(resolver.resolve("其他")).isEqualTo("OTHER");
    }
}
