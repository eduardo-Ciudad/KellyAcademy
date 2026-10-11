package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Macroeixo;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.port.MacroeixoRepositoryPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fake da taxonomia: macroeixos ordenados por ordem; problemas na ordem em que foram salvos. */
class MacroeixoRepositoryEmMemoria implements MacroeixoRepositoryPort {
    private final List<Macroeixo> macroeixos = new ArrayList<>();
    private final List<ProblemaSocial> problemas = new ArrayList<>();

    Macroeixo salvar(Macroeixo macroeixo) {
        macroeixos.add(macroeixo);
        return macroeixo;
    }

    ProblemaSocial salvar(ProblemaSocial problema) {
        problemas.add(problema);
        return problema;
    }

    @Override
    public List<Macroeixo> listarPorOrdem() {
        return macroeixos.stream().sorted(Comparator.comparingInt(Macroeixo::ordem)).toList();
    }

    @Override
    public List<ProblemaSocial> listarProblemas() {
        return List.copyOf(problemas);
    }
}
