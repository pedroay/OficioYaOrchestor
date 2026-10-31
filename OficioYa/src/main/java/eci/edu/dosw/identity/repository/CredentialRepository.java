package eci.edu.dosw.identity.repository;

import eci.edu.dosw.identity.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    Optional<Credential> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Devuelve cuantas filas borro (0 o 1). Se usa como compensacion si falla un registro. */
    long deleteByUserId(Long userId);
}
