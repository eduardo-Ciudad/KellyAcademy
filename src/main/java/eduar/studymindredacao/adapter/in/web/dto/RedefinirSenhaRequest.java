package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Token do link + senha nova. O tamanho da senha aqui só barra o absurdo: mínimo de 8 e máximo de 72 bytes ficam
 * com a PoliticaSenha, que dá a mensagem certa. O toString não mostra nenhum dos dois.
 */
public record RedefinirSenhaRequest(
        @NotBlank @Size(max = 128) String token,
        @NotNull @Size(max = 200) String novaSenha
) {
    @Override
    public String toString() {
        return "RedefinirSenhaRequest[token=***, novaSenha=***]";
    }
}
