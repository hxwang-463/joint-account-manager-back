package xyz.hxwang.jointaccountmanager.spend;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One statement upload. Records what came in and what was actually stored, so
 * an import that silently skipped rows is visible rather than invisible, and so
 * an import can be undone as a unit.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "statement_import")
public class StatementImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "filename", length = 255)
    private String filename;

    /** Lets an identical re-upload be recognised before anything is parsed. */
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** Rows found in the file. */
    @Column(name = "row_count")
    private Integer rowCount;

    /** Rows that were new. */
    @Column(name = "inserted_count")
    private Integer insertedCount;

    /** Rows already present from an earlier, overlapping upload. */
    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
