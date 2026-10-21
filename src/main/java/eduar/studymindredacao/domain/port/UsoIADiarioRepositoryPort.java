package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.UsoIADiario;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UsoIADiarioRepositoryPort {
    UsoIADiario salvar(UsoIADiario usoIADiario);

    Optional<UsoIADiario> buscarPorId(UUID id);

    Optional<UsoIADiario> buscarPorUsuarioIdEData(UUID usuarioId, LocalDate data);

    void excluirPorId(UUID id);

    boolean reservarCorrecao(UUID usuarioId, LocalDate data, int limite);

    void liberarCorrecao(UUID usuarioId, LocalDate data);

    /** Soma das correções reservadas no dia, de todos os usuários (teto global de custo). */
    int somarCorrecoesDoDia(LocalDate data);

    boolean reservarTranscricao(UUID usuarioId, LocalDate data, int limite);

    void liberarTranscricao(UUID usuarioId, LocalDate data);

    /** Soma das transcrições reservadas no dia, de todos os usuários (teto global de custo). */
    int somarTranscricoesDoDia(LocalDate data);

    void registrarTokens(UUID usuarioId, LocalDate data, int tokensEntrada, int tokensSaida);

    boolean reservarMensagemJulia(UUID usuarioId, LocalDate data, int limite);

    void liberarMensagemJulia(UUID usuarioId, LocalDate data);

    /** Soma das perguntas à Júlia no dia, de todos os usuários (teto global de custo da Júlia). */
    int somarMensagensJuliaDoDia(LocalDate data);

    /** Perguntas à Júlia feitas pelo aluno no dia (0 se ainda não há linha), para mostrar quantas restam. */
    int contarMensagensJulia(UUID usuarioId, LocalDate data);
}