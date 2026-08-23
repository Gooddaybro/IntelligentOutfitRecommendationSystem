package com.recommendation.intelligentoutfitrecommendationsystem.address;

import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressSaveRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.support.BaseMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class AddressOwnershipMySqlTests extends BaseMySqlContainerTest {

    @Autowired
    private AddressService addressService;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Test
    void ownerScopedSqlMakesForeignAndMissingAddressesIndistinguishable() {
        Long ownerId = createUser();
        Long otherId = createUser();
        Long addressId = addressService.create(ownerId, request("归属用户", "13800138000"))
                .getFirst()
                .id();

        assertThat(addressService.list(otherId)).isEmpty();
        assertThat(addressMapper.findByIdAndUserId(addressId, otherId)).isNull();

        assertSameNotFound(() -> addressService.update(otherId, addressId, request("攻击者", "13900139000")));
        assertSameNotFound(() -> addressService.update(otherId, Long.MAX_VALUE,
                request("攻击者", "13900139000")));
        assertSameNotFound(() -> addressService.delete(otherId, addressId));
        assertSameNotFound(() -> addressService.delete(otherId, Long.MAX_VALUE));
        assertSameNotFound(() -> addressService.setDefault(otherId, addressId));
        assertSameNotFound(() -> addressService.setDefault(otherId, Long.MAX_VALUE));

        assertThat(addressService.list(ownerId))
                .singleElement()
                .satisfies(address -> {
                    assertThat(address.id()).isEqualTo(addressId);
                    assertThat(address.recipientName()).isEqualTo("归属用户");
                    assertThat(address.isDefault()).isTrue();
                });
    }

    private void assertSameNotFound(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("address not found");
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("address_owner_" + UUID.randomUUID().toString().replace("-", ""));
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }

    private AddressSaveRequest request(String name, String phone) {
        return new AddressSaveRequest(name, phone, "浙江省", "杭州市", "西湖区", "文一路 1 号");
    }
}
