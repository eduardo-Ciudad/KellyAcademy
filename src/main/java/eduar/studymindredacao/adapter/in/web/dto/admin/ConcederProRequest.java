package eduar.studymindredacao.adapter.in.web.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Os limites também são conferidos no domínio (ConcessaoPro), que tira os espaços das pontas do motivo. */
public record ConcederProRequest(
        @NotNull @Min(1) @Max(365) Integer dias,
        @NotBlank @Size(min = 5, max = 300) String motivo
) {
}
