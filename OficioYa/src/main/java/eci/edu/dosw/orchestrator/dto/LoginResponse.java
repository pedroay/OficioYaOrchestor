package eci.edu.dosw.orchestrator.dto;

import eci.edu.dosw.user.Role;

import java.util.Set;

public record LoginResponse(String token, long expiresInSeconds, Long userId, Set<Role> roles) {
}