package com.recommendation.intelligentoutfitrecommendationsystem.address.api;

import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.address.dto.AddressSaveRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户的地址簿 API。
 *
 * 用户 ID 只从经过校验的 JWT 中解析，客户端不能在请求体或路径中指定地址所有者。
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(Authentication authentication) {
        return ApiResponse.ok(addressService.list(CurrentUser.from(authentication).userId()));
    }

    @PostMapping
    public ApiResponse<List<AddressResponse>> create(
            Authentication authentication,
            @Valid @RequestBody AddressSaveRequest request
    ) {
        return ApiResponse.ok(addressService.create(CurrentUser.from(authentication).userId(), request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<List<AddressResponse>> update(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressSaveRequest request
    ) {
        return ApiResponse.ok(addressService.update(CurrentUser.from(authentication).userId(), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<List<AddressResponse>> delete(
            Authentication authentication,
            @PathVariable Long addressId
    ) {
        return ApiResponse.ok(addressService.delete(CurrentUser.from(authentication).userId(), addressId));
    }

    @PutMapping("/{addressId}/default")
    public ApiResponse<List<AddressResponse>> setDefault(
            Authentication authentication,
            @PathVariable Long addressId
    ) {
        return ApiResponse.ok(addressService.setDefault(CurrentUser.from(authentication).userId(), addressId));
    }
}
