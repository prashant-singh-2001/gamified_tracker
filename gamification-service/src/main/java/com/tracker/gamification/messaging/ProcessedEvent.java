package com.tracker.gamification.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent implements Persistable<String> {
    @Id
    private String idempotencyKey;   // = logId
    private LocalDateTime processedAt;

    // Issue #82: a manually-assigned @Id with no @GeneratedValue/@Version made Spring Data's
    // default isNew() (id == null) always false, so save() compiled to em.merge() -- a racing
    // duplicate's INSERT-that-should-fail silently became an UPDATE instead. This table is
    // append-only by design (every row IS new), so isNew() is hardcoded true, forcing
    // em.persist() and making a real duplicate throw a PK violation as the guard comment claims.
    @Override
    @Transient
    public String getId() {
        return idempotencyKey;
    }

    @Override
    @Transient
    public boolean isNew() {
        return true;
    }
}