package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.ListagemRepertorios;

import java.util.List;

/**
 * Mesmos campos do PaginaResponse e mais o limitadoAoPlano: o JSON só ganha um campo,
 * então o front que já lê a página continua funcionando.
 * limitadoAoPlano = true no Grátis (um repertório por problema): o front avisa que o Pro libera todos.
 */
public record RepertorioPaginaResponse(
        List<RepertorioResumoResponse> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas,
        boolean temProxima,
        boolean limitadoAoPlano
) {
    public static RepertorioPaginaResponse de(ListagemRepertorios listagem) {
        var pagina = listagem.pagina();
        return new RepertorioPaginaResponse(
                pagina.itens().stream().map(RepertorioResumoResponse::de).toList(),
                pagina.pagina(),
                pagina.tamanho(),
                pagina.totalItens(),
                pagina.totalPaginas(),
                pagina.temProxima(),
                listagem.limitadoAoPlano()
        );
    }
}
