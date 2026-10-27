package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.PaginaResponse;
import eduar.studymindredacao.adapter.in.web.dto.admin.AdminUsuarioDetalheResponse;
import eduar.studymindredacao.adapter.in.web.dto.admin.AdminUsuarioResumoResponse;
import eduar.studymindredacao.adapter.in.web.dto.admin.AdminVisaoGeralResponse;
import eduar.studymindredacao.adapter.in.web.dto.admin.ConcederProRequest;
import eduar.studymindredacao.adapter.in.web.dto.admin.ConcessaoProResponse;
import eduar.studymindredacao.adapter.in.web.dto.admin.FiltroEmailAdmin;
import eduar.studymindredacao.adapter.in.web.dto.admin.RevogarProRequest;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.AcoesUsuarioAdminService;
import eduar.studymindredacao.application.usecase.ConsultarUsuariosAdminService;
import eduar.studymindredacao.application.usecase.ConsultarVisaoGeralAdminService;
import eduar.studymindredacao.domain.model.admin.FiltroUsuariosAdmin;
import eduar.studymindredacao.domain.model.enums.Plano;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Painel admin. A SecurityConfig só deixa chegar aqui quem tem ROLE_ADMIN no JWT; as ações ainda conferem a role
 * no banco. O id de quem age vem sempre do token, nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final ConsultarVisaoGeralAdminService visaoGeral;
    private final ConsultarUsuariosAdminService consultarUsuarios;
    private final AcoesUsuarioAdminService acoes;
    private final Clock clock;

    public AdminController(
            ConsultarVisaoGeralAdminService visaoGeral,
            ConsultarUsuariosAdminService consultarUsuarios,
            AcoesUsuarioAdminService acoes,
            Clock clock
    ) {
        this.visaoGeral = visaoGeral;
        this.consultarUsuarios = consultarUsuarios;
        this.acoes = acoes;
        this.clock = clock;
    }

    /** Números do painel para 7, 30 ou 90 dias (outro valor dá 400). */
    @GetMapping("/visao-geral")
    public AdminVisaoGeralResponse visaoGeral(@RequestParam(defaultValue = "30") int periodo) {
        return AdminVisaoGeralResponse.de(visaoGeral.consultar(periodo));
    }

    /**
     * Lista de alunos, dos cadastros mais novos para os mais antigos. Filtros opcionais e combináveis;
     * tamanho de 1 a 50 (padrão 20) e busca de até 100 caracteres, fora disso 400.
     */
    @GetMapping("/usuarios")
    public PaginaResponse<AdminUsuarioResumoResponse> listarUsuarios(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Plano plano,
            @RequestParam(required = false) FiltroEmailAdmin email,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) Integer tamanho
    ) {
        var filtro = new FiltroUsuariosAdmin(
                busca,
                plano,
                email == null ? null : email.comoVerificado(),
                pagina,
                tamanho == null ? FiltroUsuariosAdmin.TAMANHO_PADRAO : tamanho
        );
        return PaginaResponse.de(consultarUsuarios.listar(filtro), AdminUsuarioResumoResponse::de);
    }

    @GetMapping("/usuarios/{id}")
    public AdminUsuarioDetalheResponse detalharUsuario(@PathVariable UUID id) {
        return AdminUsuarioDetalheResponse.de(consultarUsuarios.detalhar(id), OffsetDateTime.now(clock));
    }

    /** Pro manual por 1 a 365 dias, com motivo. 409 se o aluno já é Pro (assinatura ou concessão vigente). */
    @PostMapping("/usuarios/{id}/concessao-pro")
    @ResponseStatus(HttpStatus.CREATED)
    public ConcessaoProResponse concederPro(
            @AuthenticationPrincipal UsuarioAutenticado admin,
            @PathVariable UUID id,
            @Valid @RequestBody ConcederProRequest request
    ) {
        var concessao = acoes.concederPro(admin.id(), id, request.dias(), request.motivo());
        return ConcessaoProResponse.de(concessao, OffsetDateTime.now(clock));
    }

    /** Revoga a concessão aberta (vale na hora). 409 se não há concessão aberta. */
    @PostMapping("/usuarios/{id}/concessao-pro/revogar")
    public ConcessaoProResponse revogarPro(
            @AuthenticationPrincipal UsuarioAutenticado admin,
            @PathVariable UUID id,
            @Valid @RequestBody RevogarProRequest request
    ) {
        var revogada = acoes.revogarPro(admin.id(), id, request.motivo());
        return ConcessaoProResponse.de(revogada, OffsetDateTime.now(clock));
    }

    /** Zera correções, transcrições e perguntas à Júlia de hoje (tokens e teto global não mudam). */
    @PostMapping("/usuarios/{id}/zerar-cota-hoje")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void zerarCotaHoje(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable UUID id) {
        acoes.zerarCotaHoje(admin.id(), id);
    }

    /** Novo link de verificação. 409 se já verificado ou verificação desligada; 429 se saiu um há menos de 5 min. */
    @PostMapping("/usuarios/{id}/reenviar-verificacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarVerificacao(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable UUID id) {
        acoes.reenviarVerificacao(admin.id(), id);
    }
}
