package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RepertorioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class BuscarRepertorioService {
    private final RepertorioRepositoryPort repertorioRepository;

    public BuscarRepertorioService(RepertorioRepositoryPort repertorioRepository) {
        this.repertorioRepository = repertorioRepository;
    }

    /** Repertório ativo pelo código; aceita minúsculas (rep-025). Inativo ou inexistente: 404. */
    public Repertorio buscar(String codigo) {
        String normalizado = codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
        return repertorioRepository.buscarAtivoPorCodigo(normalizado)
                .orElseThrow(() -> new RepertorioNaoEncontradoException(normalizado));
    }
}
