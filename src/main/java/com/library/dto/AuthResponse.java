package com.library.dto;

import com.library.model.Role;

public class AuthResponse {

    private String token;
    private String memberId;
    private String name;
    private String email;
    private Role role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String memberId, String name, String email, Role role) {
        this.token = token;
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
