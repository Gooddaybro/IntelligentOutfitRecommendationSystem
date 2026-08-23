package com.recommendation.intelligentoutfitrecommendationsystem.address;

import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressSaveRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.support.BaseMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class DefaultAddressMySqlConcurrencyTests extends BaseMySqlContainerTest {

    @Autowired
    private AddressService addressService;

    @MockitoSpyBean
    private AddressMapper addressMapper;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void simultaneousDefaultSwitchesLeaveExactlyOneDefault() throws Exception {
        Long userId = createUser();
        addressService.create(userId, request("地址一", "13800138000", "1 号"));
        Long secondId = addressService.create(userId, request("地址二", "13900139000", "2 号")).stream()
                .filter(address -> address.recipientName().equals("地址二"))
                .findFirst()
                .orElseThrow()
                .id();
        Long thirdId = addressService.create(userId, request("地址三", "13700137000", "3 号")).stream()
                .filter(address -> address.recipientName().equals("地址三"))
                .findFirst()
                .orElseThrow()
                .id();

        runTogether(
                () -> addressService.setDefault(userId, secondId),
                () -> addressService.setDefault(userId, thirdId)
        );

        assertThat(defaultCount(userId)).isOne();
        assertThat(addressService.list(userId)).filteredOn(address -> address.isDefault()).hasSize(1);
    }

    @Test
    void simultaneousFirstAddressCreatesStillChooseOneDefault() throws Exception {
        Long userId = createUser();

        runTogether(
                () -> addressService.create(userId, request("并发一", "13600136000", "1 号")),
                () -> addressService.create(userId, request("并发二", "13500135000", "2 号"))
        );

        assertThat(addressService.list(userId)).hasSize(2);
        assertThat(defaultCount(userId)).isOne();
    }

    @Test
    void serviceRollbackAfterClearingDefaultPreservesPreviousDefault() {
        Long userId = createUser();
        Long originalId = addressService.create(userId, request("原默认", "13400134000", "1 号"))
                .getFirst()
                .id();
        Long replacementId = addressService.create(userId, request("候补", "13300133000", "2 号")).stream()
                .filter(address -> address.recipientName().equals("候补"))
                .findFirst()
                .orElseThrow()
                .id();
        doThrow(new IllegalStateException("injected failure after clear"))
                .when(addressMapper)
                .setDefaultByIdAndUserId(replacementId, userId);

        assertThatThrownBy(() -> addressService.setDefault(userId, replacementId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected failure after clear");

        assertThat(defaultCount(userId)).isOne();
        assertThat(addressMapper.findByIdAndUserId(originalId, userId).getIsDefault()).isTrue();
    }

    private void runTogether(Runnable firstOperation, Runnable secondOperation) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> runWhenReleased(firstOperation, ready, start));
            Future<?> second = executor.submit(() -> runWhenReleased(secondOperation, ready, start));
            ready.await();
            start.countDown();
            for (Future<?> future : List.of(first, second)) {
                future.get();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void runWhenReleased(Runnable operation, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            operation.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", exception);
        }
    }

    private Integer defaultCount(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_address WHERE user_id = ? AND is_default = TRUE",
                Integer.class,
                userId
        );
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("address_concurrent_" + UUID.randomUUID().toString().replace("-", ""));
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }

    private AddressSaveRequest request(String name, String phone, String detail) {
        return new AddressSaveRequest(name, phone, "浙江省", "杭州市", "西湖区", detail);
    }
}
