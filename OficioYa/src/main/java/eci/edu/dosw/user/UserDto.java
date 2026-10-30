package eci.edu.dosw.user;

import java.util.Set;

/** Perfil de usuario tal como lo expone el modulo User. */
public record UserDto(Long id, String name, String email, Set<Role> roles, boolean active) {
}