package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.PaginaResponse;
import eduar.studymindredacao.adapter.in.web.dto.RepertorioDetalheResponse;
import eduar.studymindredacao.adapter.in.web.dto.RepertorioResumoResponse;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.BuscarRepertorioService;
import eduar.studymindredacao.application.usecase.ListarRepertoriosService;
import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/repertorios")
public class RepertorioController {
    private final ListarRepertoriosService listarRepertorios;
    private final BuscarRepertorioService buscarRepertorio;

    public RepertorioController(ListarRepertoriosService listarRepertorios, BuscarRepertorioService buscarRepertorio) {
        this.listarRepertorios = listarRepertorios;
        this.buscarRepertorio = buscarRepertorio;
    }

    /**
     * Listagem paginada, em ordem alfabética. Todos os filtros são opcionais e combináveis.
     * Valores inválidos (size fora de 1–50, q com mais de 100 caracteres, slug malformado, enum desconhecido) dão 400.
     */
    @GetMapping
    public PaginaResponse<RepertorioResumoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) String macroeixo,
            @RequestParam(required = false) String problema,
            @RequestParam(required = false) TipoEntidade tipo,
            @RequestParam(required = false) FuncaoArgumentativa funcao,
            @RequestParam(name = "q", required = false) String busca,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        int tamanho = size == null ? FiltroRepertorio.TAMANHO_PADRAO : size;
        var filtro = new FiltroRepertorio(macroeixo, problema, tipo, funcao, busca, page, tamanho);
        return PaginaResponse.de(listarRepertorios.listar(usuario.id(), filtro).pagina(), RepertorioResumoResponse::de);
    }

    /** Detalhe pelo código (REP-025, EVD-001, CF-007); inativo ou inexistente dá 404. */
    @GetMapping("/{codigo}")
    public RepertorioDetalheResponse buscar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                            @PathVariable String codigo) {
        return RepertorioDetalheResponse.de(buscarRepertorio.buscar(usuario.id(), codigo));
    }
}
