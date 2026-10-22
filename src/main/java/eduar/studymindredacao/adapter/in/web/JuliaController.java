package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.ConversaJuliaResponse;
import eduar.studymindredacao.adapter.in.web.dto.PerguntaJuliaRequest;
import eduar.studymindredacao.adapter.in.web.dto.TrocaJuliaResponse;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.ConsultarConversaJuliaService;
import eduar.studymindredacao.application.usecase.EncerrarConversasJuliaService;
import eduar.studymindredacao.application.usecase.EnviarMensagemJuliaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Assistente Júlia: conversa sobre a correção de uma redação do aluno logado. */
@RestController
public class JuliaController {
    private final ConsultarConversaJuliaService consultarConversa;
    private final EnviarMensagemJuliaService enviarMensagem;
    private final EncerrarConversasJuliaService encerrarConversas;

    public JuliaController(
            ConsultarConversaJuliaService consultarConversa,
            EnviarMensagemJuliaService enviarMensagem,
            EncerrarConversasJuliaService encerrarConversas
    ) {
        this.consultarConversa = consultarConversa;
        this.enviarMensagem = enviarMensagem;
        this.encerrarConversas = encerrarConversas;
    }

    @GetMapping("/redacoes/{id}/julia")
    public ConversaJuliaResponse consultar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return ConversaJuliaResponse.de(consultarConversa.consultar(usuario.id(), id));
    }

    /** Chama a IA (síncrono): pode levar alguns segundos. */
    @PostMapping("/redacoes/{id}/julia/mensagens")
    public TrocaJuliaResponse enviar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id,
            @RequestBody @Valid PerguntaJuliaRequest request
    ) {
        return TrocaJuliaResponse.de(enviarMensagem.enviar(usuario.id(), id, request.pergunta()));
    }

    /** Chamado pelo front no logout: encerra as conversas ativas do aluno. */
    @DeleteMapping("/julia/conversas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        encerrarConversas.encerrar(usuario.id());
    }
}
