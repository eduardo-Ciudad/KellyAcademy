package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.TokenInvalidoException;
import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.PoliticaSenha;
import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import eduar.studymindredacao.domain.port.GeradorTokenPort;
import eduar.studymindredacao.domain.port.SenhaEncoderPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * "Esqueci minha senha": pedir o link por e-mail e trocar a senha com ele.
 * <ul>
 *   <li>pedir o link nunca diz se a conta existe;</li>
 *   <li>só o último link enviado vale, por 30 minutos e uma única vez;</li>
 *   <li>trocar a senha sobe a versão da sessão (derruba todos os JWT), confirma o e-mail e avisa o dono.</li>
 * </ul>
 */
@Service
public class RedefinicaoSenhaService {
    /** O token real tem 43 caracteres; qualquer coisa muito maior nem é processada. */
    static final int TAMANHO_MAXIMO_TOKEN = 128;
    private static final TipoTokenUsuario TIPO = TipoTokenUsuario.REDEFINICAO_SENHA;

    private final UsuarioRepositoryPort usuarioRepository;
    private final TokenUsuarioRepositoryPort tokenRepository;
    private final GeradorTokenPort geradorToken;
    private final SenhaEncoderPort senhaEncoder;
    private final EnvioEmailPort envioEmail;
    private final PoliticaRedefinicaoSenha politica;
    private final Clock clock;

    public RedefinicaoSenhaService(
            UsuarioRepositoryPort usuarioRepository,
            TokenUsuarioRepositoryPort tokenRepository,
            GeradorTokenPort geradorToken,
            SenhaEncoderPort senhaEncoder,
            EnvioEmailPort envioEmail,
            PoliticaRedefinicaoSenha politica,
            Clock clock
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.geradorToken = geradorToken;
        this.senhaEncoder = senhaEncoder;
        this.envioEmail = envioEmail;
        this.politica = politica;
        this.clock = clock;
    }

    /**
     * Pedido de link. Para e-mail desconhecido, vazio ou com o envio desligado simplesmente não faz nada:
     * o endpoint responde igual em todos os casos. Conta ainda não verificada também recebe (redefinir confirma o
     * e-mail). Invalida os links anteriores: só o último enviado funciona.
     */
    @Transactional
    public void solicitar(String email) {
        if (!politica.habilitada() || email == null || email.isBlank()) {
            return;
        }
        usuarioRepository.buscarPorEmail(email.trim()).ifPresent(this::emitirPara);
    }

    private void emitirPara(Usuario usuario) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        tokenRepository.invalidarAtivos(usuario.id(), TIPO, agora);

        var token = geradorToken.emitir();
        tokenRepository.salvar(TokenUsuario.novo(usuario.id(), TIPO, token.hash(), agora, politica.validade()));
        envioEmail.enviarRedefinicaoSenha(new MensagemRedefinicaoSenha(
                usuario.email(), usuario.nome(), politica.linkPara(token.valorEmClaro())
        ));
    }

    /**
     * Troca a senha com o token do link. A ordem importa:
     * <ol>
     *   <li>senha fraca é recusada antes de tocar no token: o aluno corrige e usa o mesmo link;</li>
     *   <li>o token é consumido de forma atômica (inexistente, expirado, usado ou de outro tipo: mesma exceção);</li>
     *   <li>só então roda o BCrypt: token inválido nunca gasta CPU.</li>
     * </ol>
     */
    @Transactional
    public void redefinir(String token, String novaSenha) {
        PoliticaSenha.validar(novaSenha);
        if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_TOKEN) {
            throw new TokenInvalidoException();
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        UUID usuarioId = tokenRepository.consumirValido(geradorToken.hash(token.strip()), TIPO, agora)
                .orElseThrow(TokenInvalidoException::new);

        // Conta apagada depois do envio: o ON DELETE CASCADE já levaria o token, mas fica a defesa
        Usuario alterado = usuarioRepository.alterarSenha(usuarioId, senhaEncoder.codificar(novaSenha), agora)
                .orElseThrow(TokenInvalidoException::new);

        // Outros links pendentes deixam de valer: o de redefinição e o de verificação (o e-mail já foi confirmado)
        tokenRepository.invalidarAtivos(usuarioId, TIPO, agora);
        tokenRepository.invalidarAtivos(usuarioId, TipoTokenUsuario.VERIFICACAO_EMAIL, agora);
        avisarSenhaAlterada(alterado, agora);
    }

    /**
     * Aviso sem token para o dono da conta. Com o envio desligado, não sai nada. Usado também na troca logada.
     * Público de propósito: chamado através do proxy do Spring, um método package-private poderia não ser
     * repassado ao objeto real e cairia nos campos nulos do proxy.
     */
    public void avisarSenhaAlterada(Usuario usuario, OffsetDateTime quando) {
        if (!politica.habilitada()) {
            return;
        }
        envioEmail.enviarAvisoSenhaAlterada(new MensagemSenhaAlterada(
                usuario.email(), usuario.nome(), quando, politica.linkEsqueciSenha()
        ));
    }
}
