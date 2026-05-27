package com.recommendation.intelligentoutfitrecommendationsystem.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccount {

    private Long id;

    private String username;

    private String phone;

    private String email;

    private String passwordHash;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
