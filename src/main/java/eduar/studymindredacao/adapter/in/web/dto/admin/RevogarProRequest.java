package eduar.studymindredacao.adapter.in.web.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevogarProRequest(@NotBlank @Size(min = 5, max = 300) String motivo) {
}
