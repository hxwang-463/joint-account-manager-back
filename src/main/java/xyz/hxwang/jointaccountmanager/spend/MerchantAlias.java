package xyz.hxwang.jointaccountmanager.spend;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps a normalised statement descriptor to a readable merchant name and a
 * category.
 *
 * <p>The lookup key has the per-purchase noise stripped out first. Amazon
 * writes a different order id into every descriptor, so without that step each
 * purchase would look like a merchant nobody had ever seen before.
 *
 * <p>Correcting a transaction's category writes an entry here, so the fix
 * carries forward to every future purchase from the same merchant.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "merchant_alias")
public class MerchantAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lookup_key", nullable = false, length = 120)
    private String lookupKey;

    @Column(name = "merchant", nullable = false, length = 120)
    private String merchant;

    @Column(name = "category", length = 40)
    private String category;

    /** AI or USER. A USER entry is never overwritten by the classifier. */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
