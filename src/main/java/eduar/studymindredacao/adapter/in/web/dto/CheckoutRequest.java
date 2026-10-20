package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import jakarta.validation.constraints.NotNull;

/** {"ciclo": "MENSAL"} ou {"ciclo": "ANUAL"}. Valor fora do enum cai no 400 de corpo inválido. */
public record CheckoutRequest(@NotNull CicloAssinatura ciclo) {
}
