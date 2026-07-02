package com.recommendation.intelligentoutfitrecommendationsystem.behavior;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorProductSignal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class BehaviorMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private BehaviorMapper behaviorMapper;

    @Test
    void insertBehaviorEventIsIdempotentByEventId() {
        Long userId = createUser();
        BehaviorEvent event = behaviorEvent("evt-1", userId, "RECOMMENDATION_CLICKED", 1001L, 2001L);

        assertThat(behaviorMapper.insert(event)).isEqualTo(1);
        assertThat(event.getId()).isNotNull();
        assertThat(behaviorMapper.insert(behaviorEvent("evt-1", userId, "RECOMMENDATION_CLICKED", 1001L, 2001L)))
                .isZero();
    }

    @Test
    void findsRecentSignalsWithProductCategoryAndStyleTags() {
        Long userId = createUser();
        behaviorMapper.insert(behaviorEvent("evt-interest", userId, "RECOMMENDATION_CLICKED", 1002L, 2102L));

        List<BehaviorProductSignal> signals = behaviorMapper.findRecentSignals(
                userId,
                LocalDateTime.now().minusDays(30),
                20
        );

        assertThat(signals).extracting(BehaviorProductSignal::getSpuId).contains(1002L);
        assertThat(signals).extracting(BehaviorProductSignal::getCategoryName).contains("外套");
        assertThat(signals).anySatisfy(signal -> assertThat(signal.getStyleTags()).contains("commute"));
    }

    private BehaviorEvent behaviorEvent(String eventId, Long userId, String eventType, Long spuId, Long skuId) {
        BehaviorEvent event = new BehaviorEvent();
        event.setEventId(eventId);
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setSource("ASSISTANT_RECOMMENDATION");
        event.setSpuId(spuId);
        event.setSkuId(skuId);
        event.setThreadId("thread-behavior-test");
        event.setRequestId("request-behavior-test");
        event.setQuantity(1);
        event.setEventTime(LocalDateTime.now());
        event.setMetadataJson("{\"position\":1}");
        return event;
    }

    private Long createUser() {
        String username = "behavior_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
