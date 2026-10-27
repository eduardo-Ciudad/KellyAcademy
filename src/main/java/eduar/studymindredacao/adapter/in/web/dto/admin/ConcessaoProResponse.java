package eduar.studymindredacao.adapter.in.web.dto.admin;

import eduar.studymindredacao.domain.model.ConcessaoPro;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Concessão manual de Pro como o admin vê (com motivo e quem fez): nunca vai para a API do aluno. */
public record ConcessaoProResponse(
        UUID id,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        boolean vigente,
        String motivo,
        UUID concedidaPor,
        OffsetDateTime revogadaEm,
        UUID revogadaPor,
        String motivoRevogacao
) {
    public static ConcessaoProResponse de(ConcessaoPro c, OffsetDateTime agora) {
        if (c == null) {
            return null;
        }
        return new ConcessaoProResponse(c.id(), c.inicio(), c.fim(), c.vigente(agora), c.motivo(), c.concedidaPor(),
                c.revogadaEm(), c.revogadaPor(), c.motivoRevogacao());
    }
}
