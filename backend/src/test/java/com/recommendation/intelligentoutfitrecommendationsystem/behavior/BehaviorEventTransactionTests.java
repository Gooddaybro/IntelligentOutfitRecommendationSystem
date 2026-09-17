package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class BehaviorEventTransactionTests {

    @Autowired
    private BehaviorEventService behaviorEventService;

    @Test
    void strictBusinessEventRequiresExistingSpringTransaction() {
        BehaviorEventCommand command = new BehaviorEventCommand(
                "evt-order",
                10L,
                "ORDER_CREATED",
                null,
                1002L,
                2101L,
                null,
                null,
                "ORD-1",
                1,
                Map.of()
        );

        assertThatThrownBy(() -> behaviorEventService.recordBusinessEventStrict(command))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining(
                        "No existing transaction found for transaction marked with propagation 'mandatory'"
                );
    }
}
