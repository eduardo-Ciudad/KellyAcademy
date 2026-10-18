package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Troca com a conta logada. Regras da senha nova na PoliticaSenha; o toString não mostra as senhas. */
public record AlterarSenhaRequest(
        @NotBlank @Size(max = 200) String senhaAtual,
        @NotNull @Size(max = 200) String novaSenha
) {
    @Override
    public String toString() {
        return "AlterarSenhaRequest[senhaAtual=***, novaSenha=***]";
    }
}
