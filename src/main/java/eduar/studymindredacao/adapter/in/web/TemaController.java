package eduar.studymindredacao.adapter.in.web;

import eduar.studymindredacao.adapter.in.web.dto.CriarTemaPersonalizadoRequest;
import eduar.studymindredacao.adapter.in.web.dto.TemaPossivelResponse;
import eduar.studymindredacao.adapter.in.web.dto.TemaResponse;
import eduar.studymindredacao.adapter.in.web.security.UsuarioAutenticado;
import eduar.studymindredacao.application.usecase.CriarTemaPersonalizadoService;
import eduar.studymindredacao.application.usecase.ListarTemasPossiveisService;
import eduar.studymindredacao.application.usecase.ListarTemasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/temas")
public class TemaController {
    private final ListarTemasService listarTemas;
    private final ListarTemasPossiveisService listarTemasPossiveis;
    private final CriarTemaPersonalizadoService criarTemaPersonalizado;

    public TemaController(ListarTemasService listarTemas, ListarTemasPossiveisService listarTemasPossiveis,
                          CriarTemaPersonalizadoService criarTemaPersonalizado) {
        this.listarTemas = listarTemas;
        this.listarTemasPossiveis = listarTemasPossiveis;
        this.criarTemaPersonalizado = criarTemaPersonalizado;
    }

    /**
     * Os temas personalizados do aluno primeiro (origem = PERSONALIZADO), depois oficiais e autorais,
     * mais os temas possíveis que o aluno adicionou (origem = PREVISAO).
     */
    @GetMapping
    public List<TemaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return listarTemas.listarDisponiveis(usuario.id()).stream().map(TemaResponse::de).toList();
    }

    /** Os temas possíveis do ENEM, em ordem de ranking, com o material de estudo de cada um. */
    @GetMapping("/possiveis")
    public List<TemaPossivelResponse> possiveis(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return listarTemasPossiveis.listar(usuario.id()).stream().map(TemaPossivelResponse::de).toList();
    }

    /**
     * Tema digitado pelo aluno. 201 quando cria; 200 quando ele já tinha um tema com o mesmo título,
     * que é devolvido no lugar (o front usa o id do corpo nos dois casos).
     */
    @PostMapping("/personalizados")
    public ResponseEntity<TemaResponse> criarPersonalizado(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestBody @Valid CriarTemaPersonalizadoRequest request
    ) {
        var resultado = criarTemaPersonalizado.criar(usuario.id(), request.titulo());
        HttpStatus status = resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TemaResponse.de(resultado.tema()));
    }
}
