package eduar.studymindredacao.application.usecase;

import java.time.Duration;

/** Monta o VerificacaoEmailService com os fakes, ligado ou desligado, para os testes de cadastro e login. */
class CenarioVerificacao {
    static final String URL_FRONT = "https://kellyacademy.com.br";
    static final Duration VALIDADE = Duration.ofHours(24);

    final UsuarioRepositoryEmMemoria usuarios;
    final TokenUsuarioRepositoryEmMemoria tokens = new TokenUsuarioRepositoryEmMemoria();
    final GeradorTokenFake gerador = new GeradorTokenFake();
    final EnvioEmailFake emails = new EnvioEmailFake();
    final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-17T00:00:00Z");
    final VerificacaoEmailService service;

    private CenarioVerificacao(UsuarioRepositoryEmMemoria usuarios, boolean obrigatoria) {
        this.usuarios = usuarios;
        var politica = obrigatoria
                ? new PoliticaVerificacaoEmail(true, VALIDADE, URL_FRONT)
                : PoliticaVerificacaoEmail.desligada();
        this.service = new VerificacaoEmailService(usuarios, tokens, gerador, emails, politica, relogio);
    }

    static CenarioVerificacao ligada(UsuarioRepositoryEmMemoria usuarios) {
        return new CenarioVerificacao(usuarios, true);
    }

    static CenarioVerificacao desligada(UsuarioRepositoryEmMemoria usuarios) {
        return new CenarioVerificacao(usuarios, false);
    }
}
