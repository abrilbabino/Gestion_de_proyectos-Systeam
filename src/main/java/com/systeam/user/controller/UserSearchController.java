package com.systeam.user.controller;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systeam.user.dto.UserSearchResponse;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {

    private final JdbcTemplate jdbc;

    public UserSearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public List<UserSearchResponse> search(@RequestParam String username) {
        String pattern = "%" + username + "%";
        return jdbc.query(
            "SELECT id, name, wallet_address FROM users " +
            "WHERE (name ILIKE ? OR email ILIKE ?) AND enabled = true LIMIT 20",
            (rs, rowNum) -> UserSearchResponse.builder()
                .id(rs.getLong("id"))
                .username(rs.getString("name"))
                .walletAddress(rs.getString("wallet_address"))
                .build(),
            pattern, pattern
        );
    }
}
