package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.adapter.out.security.JwtTokenAdapter;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Monta os casos de uso de senha com os fakes (e o JWT de verdade, para provar que a sessão cai), com o envio
 * de e-mail ligado ou desligado.
 */
class CenarioSenha {
    static final String URL_FRONT = "https://kellyacademy.com.br";
    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final String SENHA_ANTIGA = "senhaAntiga123";

    /** Conta quantas vezes o BCrypt (fake) rodou. */
    static class EncoderQueConta extends SenhaEncoderFake {
        int codificacoes;

        @Override
        public String codificar(String senhaPura) {
            codificacoes++;
            return super.codificar(senhaPura);
        }
    }

    final UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
    final TokenUsuarioRepositoryEmMemoria tokens = new TokenUsuarioRepositoryEmMemoria();
    final GeradorTokenFake gerador = new GeradorTokenFake();
    final EnvioEmailFake emails = new EnvioEmailFake();
    final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-18T17:00:00Z");
    final EncoderQueConta encoder = new EncoderQueConta();
    final JwtTokenAdapter jwt = new JwtTokenAdapter("segredo-de-teste-com-pelo-menos-32-caracteres", "studymind-redacao", 60);
    final ValidarSessaoService validarSessao = new ValidarSessaoService(jwt, usuarios);
    final RedefinicaoSenhaService redefinicao;
    final AlterarSenhaService alterar;

    private CenarioSenha(boolean emailLigado) {
        var politica = emailLigado
                ? new PoliticaRedefinicaoSenha(true, VALIDADE, URL_FRONT)
                : PoliticaRedefinicaoSenha.desligada();
        redefinicao = new RedefinicaoSenhaService(usuarios, tokens, gerador, encoder, emails, politica, relogio);
        alterar = new AlterarSenhaService(usuarios, tokens, encoder, jwt, redefinicao, relogio);
    }

    static CenarioSenha ligado() {
        return new CenarioSenha(true);
    }

    static CenarioSenha desligado() {
        return new CenarioSenha(false);
    }

    OffsetDateTime agora() {
        return OffsetDateTime.now(relogio);
    }

    /** Conta com a senha {@link #SENHA_ANTIGA}; verificada (na data informada) ou não (null). */
    Usuario salvar(String nome, String email, OffsetDateTime verificadaEm) {
        return usuarios.salvar(new Usuario(null, nome, email, "hash:" + SENHA_ANTIGA, Role.ALUNO,
                agora().minusDays(10), verificadaEm));
    }
}
