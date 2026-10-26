package eduar.studymindredacao.domain.model.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;


public enum Plano {
    GRATIS(3, 2, EnumSet.noneOf(RecursoPro.class)),
    PRO(10, 10, EnumSet.allOf(RecursoPro.class));

    private final int correcoesPorDia;
    private final int transcricoesPorDia;
    private final Set<RecursoPro> recursos;

    Plano(int correcoesPorDia, int transcricoesPorDia, Set<RecursoPro> recursos) {
        this.correcoesPorDia = correcoesPorDia;
        this.transcricoesPorDia = transcricoesPorDia;
        this.recursos = Collections.unmodifiableSet(recursos);
    }


    public int limiteDiario(RecursoIA recurso) {
        Objects.requireNonNull(recurso, "recurso é obrigatório");
        return switch (recurso) {
            case CORRECAO -> correcoesPorDia;
            case TRANSCRICAO -> transcricoesPorDia;
            case JULIA -> throw new IllegalArgumentException("o limite diário da Júlia vem da PoliticaJulia");
        };
    }

    public boolean libera(RecursoPro recurso) {
        Objects.requireNonNull(recurso, "recurso é obrigatório");
        return recursos.contains(recurso);
    }
}
