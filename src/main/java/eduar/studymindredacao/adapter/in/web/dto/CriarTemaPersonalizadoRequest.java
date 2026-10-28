package eduar.studymindredacao.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O teto de 500 só barra corpo exagerado; a regra de 10 a 200 caracteres
 * (depois de normalizar os espaços) fica no domínio, em Tema.personalizado.
 */
public record CriarTemaPersonalizadoRequest(@NotBlank @Size(max = 500) String titulo) {
}
