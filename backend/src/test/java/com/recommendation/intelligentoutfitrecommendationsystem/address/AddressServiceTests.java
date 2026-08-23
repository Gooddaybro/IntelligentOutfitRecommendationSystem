package com.recommendation.intelligentoutfitrecommendationsystem.address;

import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressSaveRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTests {

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressService service;

    @Test
    void firstAddressBecomesDefaultWithoutTrustingClientState() {
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.countByUserId(10L)).thenReturn(0);
        when(addressMapper.findAllByUserId(10L)).thenReturn(List.of());

        service.create(10L, request("张三", "13800138000", "幸福路 1 号"));

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getIsDefault()).isTrue();
    }

    @Test
    void laterAddressDoesNotReplaceCurrentDefault() {
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.countByUserId(10L)).thenReturn(1);
        when(addressMapper.findAllByUserId(10L)).thenReturn(List.of());

        service.create(10L, request("李四", "13900139000", "平安街 2 号"));

        ArgumentCaptor<UserAddress> captor = ArgumentCaptor.forClass(UserAddress.class);
        verify(addressMapper).insert(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isFalse();
    }

    @Test
    void switchingDefaultClearsAndSetsInsideOwnerScopedMutation() {
        UserAddress target = address(22L, 10L, false);
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.findByIdAndUserId(22L, 10L)).thenReturn(target);
        when(addressMapper.setDefaultByIdAndUserId(22L, 10L)).thenReturn(1);
        when(addressMapper.findAllByUserId(10L)).thenReturn(List.of(target));

        service.setDefault(10L, 22L);

        verify(addressMapper).clearDefaultByUserId(10L);
        verify(addressMapper).setDefaultByIdAndUserId(22L, 10L);
    }

    @Test
    void deletingDefaultPromotesMostRecentlyUpdatedRemainingAddress() {
        UserAddress deleted = address(21L, 10L, true);
        UserAddress replacement = address(22L, 10L, false);
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.findByIdAndUserId(21L, 10L)).thenReturn(deleted);
        when(addressMapper.deleteByIdAndUserId(21L, 10L)).thenReturn(1);
        when(addressMapper.findReplacementByUserId(10L)).thenReturn(replacement);
        when(addressMapper.setDefaultByIdAndUserId(22L, 10L)).thenReturn(1);
        when(addressMapper.findAllByUserId(10L)).thenReturn(List.of(replacement));

        service.delete(10L, 21L);

        verify(addressMapper).setDefaultByIdAndUserId(22L, 10L);
    }

    @Test
    void deletingLastDefaultAllowsEmptyAddressBook() {
        UserAddress deleted = address(21L, 10L, true);
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.findByIdAndUserId(21L, 10L)).thenReturn(deleted);
        when(addressMapper.deleteByIdAndUserId(21L, 10L)).thenReturn(1);
        when(addressMapper.findAllByUserId(10L)).thenReturn(List.of());

        service.delete(10L, 21L);

        verify(addressMapper, never()).setDefaultByIdAndUserId(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void foreignAndMissingAddressHaveSamePublicError() {
        when(addressMapper.lockUserById(10L)).thenReturn(10L);
        when(addressMapper.findByIdAndUserId(999L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(10L, 999L,
                request("王五", "13700137000", "建设路 3 号")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("address not found");

        verify(addressMapper, never()).updateByIdAndUserId(org.mockito.ArgumentMatchers.any(UserAddress.class));
    }

    private AddressSaveRequest request(String recipientName, String phone, String detail) {
        return new AddressSaveRequest(recipientName, phone, "浙江省", "杭州市", "西湖区", detail);
    }

    private UserAddress address(Long id, Long userId, boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setRecipientName("测试用户");
        address.setPhone("13800138000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("西湖区");
        address.setDetail("文一路 1 号");
        address.setIsDefault(isDefault);
        return address;
    }
}
