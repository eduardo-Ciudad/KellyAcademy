package eduar.studymindredacao.adapter.out.persistence.entity;

import eduar.studymindredacao.domain.model.enums.OrigemTema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "temas")
@Getter
@Setter
@NoArgsConstructor
public class TemaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String titulo;

    @Column(name = "textos_motivadores", columnDefinition = "TEXT")
    private String textosMotivadores;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemTema origem;

    private Short ano;

    @Column(nullable = false)
    private Boolean ativo;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    /** Só nos temas PERSONALIZADO (V26). */
    @Column(name = "criado_por_id", updatable = false)
    private UUID criadoPorId;
}
