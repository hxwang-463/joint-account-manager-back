package xyz.hxwang.jointaccountmanager.spend;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A spending category, stored as data rather than a Java enum so a one-off —
 * an immigration fee, a car repair — can be added without a redeploy.
 *
 * <p>The classifier is shown the current list and told to reuse it. A category
 * it proposes is stored as PROPOSED for review; approving it puts it in the
 * list the classifier sees next time, which is what stops the same concept
 * reappearing under three slightly different names.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Uppercase, underscore-separated. Normalised on write so casing variants cannot coexist. */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    /** CORE for the seeded set, AD_HOC for anything added later. */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    /** ACTIVE, PROPOSED (awaiting review) or MERGED (superseded). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** SEED, AI or USER. */
    @Column(name = "created_by", nullable = false, length = 20)
    private String createdBy;

    /** Where this category's transactions should be read as belonging, once merged. */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
