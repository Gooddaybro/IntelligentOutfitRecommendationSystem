package com.recommendation.intelligentoutfitrecommendationsystem.address.service;

import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressSaveRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 当前用户地址簿的业务边界。
 *
 * 所有变更先锁定当前用户行，使一个用户的地址写入顺序化；同时所有目标地址 SQL 都携带
 * userId，确保随机不存在 ID 和其他用户地址使用相同的公开错误语义。
 */
@Service
public class AddressService {

    private static final String ADDRESS_NOT_FOUND = "address not found";

    private final AddressMapper addressMapper;

    public AddressService(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /**
     * 返回当前用户地址簿，默认地址优先，其余按最近更新时间倒序排列。
     */
    public List<AddressResponse> list(Long userId) {
        validateUserId(userId);
        return currentList(userId);
    }

    /**
     * 校验指定地址属于当前用户，供结算等下游模块复用同一所有权和错误语义。
     *
     * @return 不包含 userId 的地址快照
     * @throws ResourceNotFoundException 地址不存在或不属于当前用户时抛出相同异常
     */
    public AddressResponse requireOwnedAddress(Long userId, Long addressId) {
        validateUserId(userId);
        return AddressResponse.from(requireOwnedAddressEntity(userId, addressId));
    }

    /**
     * 创建地址并返回刷新后的完整地址簿；第一条地址由服务端自动标记为默认。
     */
    @Transactional
    public List<AddressResponse> create(Long userId, AddressSaveRequest request) {
        lockUser(userId);
        UserAddress address = fromRequest(null, userId, request);
        address.setIsDefault(addressMapper.countByUserId(userId) == 0);
        addressMapper.insert(address);
        return currentList(userId);
    }

    /**
     * 更新当前用户拥有的地址并返回刷新后的完整地址簿。
     *
     * @throws ResourceNotFoundException 地址不存在或不属于当前用户时抛出相同异常
     */
    @Transactional
    public List<AddressResponse> update(Long userId, Long addressId, AddressSaveRequest request) {
        lockUser(userId);
        requireOwnedAddressEntity(userId, addressId);
        UserAddress address = fromRequest(addressId, userId, request);
        if (addressMapper.updateByIdAndUserId(address) == 0) {
            throw notFound();
        }
        return currentList(userId);
    }

    /**
     * 删除当前用户地址并返回刷新后的完整地址簿；删除默认项时自动提升最近更新的候补项。
     *
     * @throws ResourceNotFoundException 地址不存在或不属于当前用户时抛出相同异常
     */
    @Transactional
    public List<AddressResponse> delete(Long userId, Long addressId) {
        lockUser(userId);
        UserAddress existing = requireOwnedAddressEntity(userId, addressId);
        if (addressMapper.deleteByIdAndUserId(addressId, userId) == 0) {
            throw notFound();
        }
        if (Boolean.TRUE.equals(existing.getIsDefault())) {
            UserAddress replacement = addressMapper.findReplacementByUserId(userId);
            if (replacement != null) {
                addressMapper.setDefaultByIdAndUserId(replacement.getId(), userId);
            }
        }
        return currentList(userId);
    }

    /**
     * 在一个用户级串行事务中清除旧默认并设置新默认，随后返回刷新后的完整地址簿。
     *
     * @throws ResourceNotFoundException 地址不存在或不属于当前用户时抛出相同异常
     */
    @Transactional
    public List<AddressResponse> setDefault(Long userId, Long addressId) {
        lockUser(userId);
        requireOwnedAddressEntity(userId, addressId);
        addressMapper.clearDefaultByUserId(userId);
        if (addressMapper.setDefaultByIdAndUserId(addressId, userId) == 0) {
            throw notFound();
        }
        return currentList(userId);
    }

    private void lockUser(Long userId) {
        validateUserId(userId);
        if (addressMapper.lockUserById(userId) == null) {
            throw new ResourceNotFoundException("user not found");
        }
    }

    private UserAddress requireOwnedAddressEntity(Long userId, Long addressId) {
        validateAddressId(addressId);
        UserAddress address = addressMapper.findByIdAndUserId(addressId, userId);
        if (address == null) {
            throw notFound();
        }
        return address;
    }

    /**
     * 在持久化边界统一去除地址字段首尾空白，避免展示差异进入地址快照和后续订单合同。
     *
     * @param id 更新时的地址 ID；创建时为 null
     * @param userId 当前认证用户 ID
     * @param request 已通过 API 字段约束校验的地址请求
     * @return 可安全持久化的用户地址实体
     */
    private UserAddress fromRequest(Long id, Long userId, AddressSaveRequest request) {
        UserAddress address = new UserAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetail(request.detail().trim());
        return address;
    }

    private List<AddressResponse> currentList(Long userId) {
        return addressMapper.findAllByUserId(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(ADDRESS_NOT_FOUND);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }

    private void validateAddressId(Long addressId) {
        if (addressId == null || addressId <= 0) {
            throw new BadRequestException("addressId must be positive");
        }
    }
}
