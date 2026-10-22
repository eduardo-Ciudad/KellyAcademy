package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O limite de 500 também é conferido no domínio (depois de tirar os espaços das pontas). */
public record PerguntaJuliaRequest(@NotBlank @Size(max = 500) String pergunta) {
}
