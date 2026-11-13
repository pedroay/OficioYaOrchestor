package eci.edu.dosw.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import eci.edu.dosw.orchestrator.dto.PhoneDTO;
import java.util.List;
import java.util.UUID;

/**
 * Perfil de usuario tal como lo expone el modulo User.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        UUID id,
        String name,
        String email,
        List<PhoneDTO> phones,
        List<String> roles,
        String accountStatus,
        Boolean active) {

    public UserDto {
        if (active == null) {
            active = isStatusActive(accountStatus);
        }
    }

    public UserDto(UUID id, String name, String email, List<String> roles, String accountStatus) {
        this(id, name, email, List.of(), roles, accountStatus, isStatusActive(accountStatus));
    }

    public UserDto(UUID id, String name, String email, List<PhoneDTO> phones, List<String> roles, String accountStatus) {
        this(id, name, email, phones, roles, accountStatus, isStatusActive(accountStatus));
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active) || isStatusActive(accountStatus);
    }

    private static boolean isStatusActive(String status) {
        return status != null && status.equalsIgnoreCase("ACTIVE");
    }
}
