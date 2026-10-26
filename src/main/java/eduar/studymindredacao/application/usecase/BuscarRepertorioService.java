package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.exception.RepertorioNaoEncontradoException;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class BuscarRepertorioService {
    private final RepertorioRepositoryPort repertorioRepository;
    private final ConsultarPlanoService consultarPlano;

    public BuscarRepertorioService(RepertorioRepositoryPort repertorioRepository, ConsultarPlanoService consultarPlano) {
        this.repertorioRepository = repertorioRepository;
        this.consultarPlano = consultarPlano;
    }

    /**
     * Repertório ativo pelo código; aceita minúsculas (rep-025). Inativo ou inexistente: 404, para qualquer plano.
     * No Grátis, um código que não está entre os liberados dá 403 (RecursoExclusivoProException).
     */
    public Repertorio buscar(UUID usuarioId, String codigo) {
        String normalizado = codigo == null ? "" : codigo.strip().toUpperCase(Locale.ROOT);
        Repertorio repertorio = repertorioRepository.buscarAtivoPorCodigo(normalizado)
                .orElseThrow(() -> new RepertorioNaoEncontradoException(normalizado));

        boolean completo = consultarPlano.doUsuario(usuarioId).libera(RecursoPro.REPERTORIO_COMPLETO);
        if (!completo && !repertorioRepository.liberadoNoGratis(normalizado)) {
            throw new RecursoExclusivoProException(RecursoPro.REPERTORIO_COMPLETO);
        }
        return repertorio;
    }
}
