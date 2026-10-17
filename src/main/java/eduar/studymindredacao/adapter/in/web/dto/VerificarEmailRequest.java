package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Token do link de verificação. O toString não mostra o token: ele é uma credencial de uso único. */
public record VerificarEmailRequest(@NotBlank @Size(max = 128) String token) {
    @Override
    public String toString() {
        return "VerificarEmailRequest[token=***]";
    }
}
