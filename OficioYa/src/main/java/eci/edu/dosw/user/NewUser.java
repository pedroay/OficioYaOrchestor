package eci.edu.dosw.user;

import java.util.Set;

/** Datos para crear un perfil en el modulo User. */
public record NewUser(String name, String email, Set<Role> roles) {
}