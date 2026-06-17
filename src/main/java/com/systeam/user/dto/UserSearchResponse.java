package com.systeam.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchResponse {
    private Long id;
    private String username;
    private String walletAddress;
}
