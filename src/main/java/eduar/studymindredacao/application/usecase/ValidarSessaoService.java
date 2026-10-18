package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.DadosToken;
import eduar.studymindredacao.domain.port.TokenPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Decide se um JWT ainda abre a sessão. Além da assinatura e da expiração (TokenPort), confere se a versão da
 * sessão gravada no token é a mesma do banco: trocar a senha sobe a versão, e todo token emitido antes deixa de
 * valer na hora, sem esperar expirar. Conta apagada também derruba o token.
 * <p>
 * Custa um SELECT pela chave primária por requisição autenticada. Com o banco fora do ar a exceção sobe e a
 * requisição falha: nunca deixa passar um token sem conferir a versão.
 */
@Service
public class ValidarSessaoService {
    private final TokenPort tokenPort;
    private final UsuarioRepositoryPort usuarioRepository;

    public ValidarSessaoService(TokenPort tokenPort, UsuarioRepositoryPort usuarioRepository) {
        this.tokenPort = tokenPort;
        this.usuarioRepository = usuarioRepository;
    }

    public Optional<DadosToken> validar(String jwt) {
        // JWT inválido sai aqui, sem consultar o banco: token lixo não gera carga no Postgres
        return tokenPort.validar(jwt)
                .filter(dados -> usuarioRepository.buscarVersaoSessao(dados.usuarioId())
                        // igualdade, não ">=": versão acima da do banco também não é um token que emitimos
                        .map(versao -> versao == dados.versaoSessao())
                        .orElse(false));
    }
}
