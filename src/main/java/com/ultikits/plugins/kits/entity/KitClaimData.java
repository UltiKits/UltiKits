package com.ultikits.plugins.kits.entity;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.annotations.Column;
import com.ultikits.ultitools.annotations.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("kits_claims")
public class KitClaimData extends BaseDataEntity<String> {

    @Column("uuid")
    private String uuid;

    @Column("player_uuid")
    private String playerUuid;

    @Column("kit_name")
    private String kitName;

    @Column("last_claim")
    private long lastClaim;

    @Column("claim_count")
    private int claimCount;

    @Override
    public String getId() {
        return uuid;
    }

    @Override
    public void setId(String id) {
        this.uuid = id;
    }
}
