package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.port.MacroeixoRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ListarMacroeixosService {
    private final MacroeixoRepositoryPort macroeixoRepository;

    public ListarMacroeixosService(MacroeixoRepositoryPort macroeixoRepository) {
        this.macroeixoRepository = macroeixoRepository;
    }

    /** Macroeixos na ordem de exibição, cada um com seus problemas sociais (na ordem que a porta devolve). */
    public List<MacroeixoComProblemas> listar() {
        Map<String, List<ProblemaSocial>> problemasPorMacroeixo = macroeixoRepository.listarProblemas().stream()
                .collect(Collectors.groupingBy(ProblemaSocial::macroeixoSlug));

        return macroeixoRepository.listarPorOrdem().stream()
                .map(m -> new MacroeixoComProblemas(m, problemasPorMacroeixo.getOrDefault(m.slug(), List.of())))
                .toList();
    }
}
