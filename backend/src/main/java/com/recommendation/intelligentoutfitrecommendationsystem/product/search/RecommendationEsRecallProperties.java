package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether assistant recommendation candidates use Elasticsearch as the first recall stage.
 *
 * <p>The switch is separate from storefront search so recommendation rollout can stay conservative while
 * product search remains available as an independently testable capability.</p>
 */
@ConfigurationProperties(prefix = "app.recommendation.es-recall")
public class RecommendationEsRecallProperties {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 500;

    private boolean enabled;
    private int limit = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimit() {
        return Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
