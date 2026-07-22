package com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis boundary for admin user-account projections and status writes, keeping user SQL out of services.
 */
@Mapper
public interface AdminUserMapper {
    /**
     * Loads the admin user table projection with order aggregates already shaped for the API contract.
     *
     * @return users ordered by database id for stable admin-console pagination assumptions
     */
    List<AdminUserResponse> findUsers();

    /**
     * Reloads one admin user projection after writes so callers return database-normalized state.
     *
     * @param userId positive user id owned by the Java user-account database boundary
     * @return matching user projection, or {@code null} when the account no longer exists
     */
    AdminUserResponse findUserById(@Param("userId") Long userId);

    /**
     * Stores the canonical database status produced by the service validation boundary.
     *
     * @param userId positive user id to update
     * @param status canonical database status, limited by service code to active or disabled
     * @return affected row count so the service can translate missing users to API errors
     */
    int updateUserStatus(@Param("userId") Long userId, @Param("status") String status);
}
