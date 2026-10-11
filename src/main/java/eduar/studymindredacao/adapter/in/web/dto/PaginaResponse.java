package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.Pagina;

import java.util.List;
import java.util.function.Function;

/** Página genérica da API. Os campos calculados vão prontos para o front não refazer a conta. */
public record PaginaResponse<T>(
        List<T> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas,
        boolean temProxima
) {
    public static <D, T> PaginaResponse<T> de(Pagina<D> pagina, Function<D, T> conversor) {
        return new PaginaResponse<>(
                pagina.itens().stream().map(conversor).toList(),
                pagina.pagina(),
                pagina.tamanho(),
                pagina.totalItens(),
                pagina.totalPaginas(),
                pagina.temProxima()
        );
    }
}
