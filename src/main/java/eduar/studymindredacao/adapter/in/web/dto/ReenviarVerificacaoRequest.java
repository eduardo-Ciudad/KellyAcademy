package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReenviarVerificacaoRequest(@NotBlank @Email @Size(max = 150) String email) {
}
