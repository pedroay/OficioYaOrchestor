package eci.edu.dosw.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhoneDTO(
        @NotBlank(message = "El número de teléfono es obligatorio") String number,
        Boolean principal) {

    public PhoneDTO {
        if (principal == null) {
            principal = false;
        }
    }
}

