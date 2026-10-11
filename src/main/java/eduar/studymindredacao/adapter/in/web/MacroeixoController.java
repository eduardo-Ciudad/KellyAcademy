package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.MacroeixoResponse;
import eduar.studymindredacao.application.usecase.ListarMacroeixosService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/macroeixos")
public class MacroeixoController {
    private final ListarMacroeixosService listarMacroeixos;

    public MacroeixoController(ListarMacroeixosService listarMacroeixos) {
        this.listarMacroeixos = listarMacroeixos;
    }

    /** Macroeixos na ordem de exibição, cada um com seus problemas: alimenta os filtros do Repertório. */
    @GetMapping
    public List<MacroeixoResponse> listar() {
        return listarMacroeixos.listar().stream().map(MacroeixoResponse::de).toList();
    }
}
