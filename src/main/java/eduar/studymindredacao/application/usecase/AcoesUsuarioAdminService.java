package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.AcaoAdminConflitoException;
import eduar.studymindredacao.domain.exception.AcessoAdminNegadoException;
import eduar.studymindredacao.domain.exception.ConcessaoProConflitoException;
import eduar.studymindredacao.domain.exception.ReenvioMuitoRecenteException;
import eduar.studymindredacao.domain.exception.UsuarioNaoEncontradoException;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.OrigemPlano;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ações do admin sobre um aluno. Toda ação começa conferindo, no banco, que quem pede ainda é ADMIN: a role do JWT
 * pode estar desatualizada até o token expirar. Cada ação deixa uma linha de log (ids e ação; nunca motivo nem e-mail).
 */
@Service
public class AcoesUsuarioAdminService {
    private static final Logger log = LoggerFactory.getLogger(AcoesUsuarioAdminService.class);
    /** Intervalo mínimo entre dois links de verificação para o mesmo aluno (não lotar a caixa de e-mail dele). */
    static final Duration INTERVALO_REENVIO = Duration.ofMinutes(5);

    private final UsuarioRepositoryPort usuarios;
    private final ConcessaoProRepositoryPort concessoes;
    private final ConsultarPlanoService consultarPlano;
    private final UsoIADiarioRepositoryPort usoDiario;
    private final VerificacaoEmailService verificacaoEmail;
    private final TokenUsuarioRepositoryPort tokens;
    private final Clock clock;

    public AcoesUsuarioAdminService(
            UsuarioRepositoryPort usuarios,
            ConcessaoProRepositoryPort concessoes,
            ConsultarPlanoService consultarPlano,
            UsoIADiarioRepositoryPort usoDiario,
            VerificacaoEmailService verificacaoEmail,
            TokenUsuarioRepositoryPort tokens,
            Clock clock
    ) {
        this.usuarios = usuarios;
        this.concessoes = concessoes;
        this.consultarPlano = consultarPlano;
        this.usoDiario = usoDiario;
        this.verificacaoEmail = verificacaoEmail;
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Dá Pro manual por {@code dias} (1 a 365). Uma concessão vencida e nunca revogada é fechada antes, na mesma
     * transação. Conceder para a própria conta é permitido (fica registrado igual).
     *
     * @throws ConcessaoProConflitoException o aluno já é Pro pela assinatura ou já tem concessão vigente
     */
    @Transactional
    public ConcessaoPro concederPro(UUID adminId, UUID alunoId, int dias, String motivo) {
        exigirAdmin(adminId);
        Usuario aluno = buscarAluno(alunoId);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        // Valida prazo e motivo antes de olhar o estado: entrada inválida é 400, não 409
        ConcessaoPro nova = ConcessaoPro.conceder(aluno.id(), adminId, motivo, dias, agora, clock.getZone());

        if (consultarPlano.detalhar(aluno.id()).origem() == OrigemPlano.ASSINATURA) {
            throw new ConcessaoProConflitoException("Este aluno já é Pro pela assinatura.");
        }
        Optional<ConcessaoPro> aberta = concessoes.buscarAberta(aluno.id());
        if (aberta.isPresent()) {
            if (!aberta.get().vencida(agora)) {
                throw new ConcessaoProConflitoException(
                        "Este aluno já tem uma concessão de Pro ativa. Revogue-a antes de conceder outra.");
            }
            concessoes.salvar(aberta.get().fecharVencida(adminId, agora));
        }

        ConcessaoPro salva = concessoes.salvar(nova);
        log.info("Admin {} concedeu Pro manual ao usuário {} por {} dias (concessão {})",
                adminId, aluno.id(), dias, salva.id());
        return salva;
    }

    /** @throws ConcessaoProConflitoException o aluno não tem concessão aberta */
    @Transactional
    public ConcessaoPro revogarPro(UUID adminId, UUID alunoId, String motivo) {
        exigirAdmin(adminId);
        Usuario aluno = buscarAluno(alunoId);
        ConcessaoPro aberta = concessoes.buscarAberta(aluno.id())
                .orElseThrow(() -> new ConcessaoProConflitoException("Este aluno não tem concessão de Pro ativa."));

        ConcessaoPro revogada = concessoes.salvar(aberta.revogar(adminId, motivo, OffsetDateTime.now(clock)));
        log.info("Admin {} revogou a concessão {} do usuário {}", adminId, revogada.id(), aluno.id());
        return revogada;
    }

    /**
     * Zera correções, transcrições e perguntas à Júlia de hoje. Tokens e o contador global continuam como estão.
     *
     * @return true se o aluno tinha uso hoje
     */
    @Transactional
    public boolean zerarCotaHoje(UUID adminId, UUID alunoId) {
        exigirAdmin(adminId);
        Usuario aluno = buscarAluno(alunoId);
        LocalDate hoje = LocalDate.now(clock);

        boolean zerou = usoDiario.zerarContadores(aluno.id(), hoje);
        log.info("Admin {} zerou a cota de {} do usuário {} (havia uso: {})", adminId, hoje, aluno.id(), zerou);
        return zerou;
    }

    /**
     * Manda um novo link de verificação (invalida os anteriores, como no cadastro).
     *
     * @throws AcaoAdminConflitoException  verificação desligada no ambiente ou e-mail já verificado
     * @throws ReenvioMuitoRecenteException já saiu um link para esse aluno há menos de 5 minutos
     */
    @Transactional
    public void reenviarVerificacao(UUID adminId, UUID alunoId) {
        exigirAdmin(adminId);
        Usuario aluno = buscarAluno(alunoId);
        if (!verificacaoEmail.verificacaoObrigatoria()) {
            throw new AcaoAdminConflitoException("A verificação de e-mail está desligada neste ambiente.");
        }
        if (aluno.emailVerificado()) {
            throw new AcaoAdminConflitoException("O e-mail deste aluno já foi verificado.");
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        tokens.ultimaEmissao(aluno.id(), TipoTokenUsuario.VERIFICACAO_EMAIL)
                .map(ultima -> ultima.plus(INTERVALO_REENVIO))
                .filter(liberaEm -> liberaEm.isAfter(agora))
                .ifPresent(liberaEm -> {
                    throw new ReenvioMuitoRecenteException(Duration.between(agora, liberaEm).toSeconds());
                });

        verificacaoEmail.emitirPara(aluno);
        log.info("Admin {} reenviou o link de verificação do usuário {}", adminId, aluno.id());
    }

    /** A role vem do banco, não do JWT. Conta apagada ou rebaixada para ALUNO é barrada. */
    private void exigirAdmin(UUID adminId) {
        if (adminId == null) {
            throw new AcessoAdminNegadoException();
        }
        usuarios.buscarPorId(adminId)
                .filter(usuario -> usuario.role() == Role.ADMIN)
                .orElseThrow(() -> {
                    log.warn("Ação de admin recusada: usuário {} não é ADMIN no banco", adminId);
                    return new AcessoAdminNegadoException();
                });
    }

    private Usuario buscarAluno(UUID alunoId) {
        if (alunoId == null) {
            throw new UsuarioNaoEncontradoException();
        }
        return usuarios.buscarPorId(alunoId).orElseThrow(UsuarioNaoEncontradoException::new);
    }
}
