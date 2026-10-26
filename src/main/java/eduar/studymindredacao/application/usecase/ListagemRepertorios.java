package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;

import java.util.Objects;

/**
 * Página de repertórios e se ela foi cortada pelo plano.
 * limitadoAoPlano = true no Grátis: o front avisa que o Pro libera todos.
 */
public record ListagemRepertorios(Pagina<Repertorio> pagina, boolean limitadoAoPlano) {
    public ListagemRepertorios {
        Objects.requireNonNull(pagina, "pagina é obrigatória");
    }
}
