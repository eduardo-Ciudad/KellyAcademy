package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.Usuario;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositoryPort {
    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorId(UUID id);

    void excluirPorId(UUID id);

    Optional<Usuario> buscarPorEmail(String email);

    boolean existePorEmail(String email);

    /**
     * Único caminho para trocar a senha: num UPDATE atômico grava o hash novo, sobe a versão da sessão
     * (derruba os JWT emitidos antes), guarda a data e confirma o e-mail se ainda não estava.
     * {@link #salvar(Usuario)} não altera senha nem versão de uma conta que já existe.
     *
     * @return o usuário já atualizado, ou vazio se a conta não existe
     */
    Optional<Usuario> alterarSenha(UUID id, String novoSenhaHash, OffsetDateTime quando);

    /** Versão atual da sessão, comparada com a do JWT a cada requisição. Vazio se a conta não existe. */
    Optional<Integer> buscarVersaoSessao(UUID id);
}
