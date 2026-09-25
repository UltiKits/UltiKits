package com.ultikits.plugins.kits.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;

@Data
public class KitDefinition {
    private String name;
    private String displayName = "&7Kit";
    private List<String> description = new ArrayList<>();
    private String icon = "CHEST";
    private double price = 0;
    private int levelRequired = 0;
    private String permission = "";
    private boolean reBuyable = false;
    private long cooldown = 0;
    private List<String> playerCommands = new ArrayList<>();
    private List<String> consoleCommands = new ArrayList<>();
    private String items = "";
    /**
     * True while {@link #displayName} is the catalogue's fallback because the kit file has no
     * {@code displayName}. Saving the kit then leaves the key out, so the name keeps following the
     * server's language instead of being written down in whichever language saved the file.
     */
    @EqualsAndHashCode.Exclude
    private boolean displayNameFromCatalogue;

    /** Sets a display name that belongs to the kit, and is therefore saved with it. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.displayNameFromCatalogue = false;
    }

    /** Shows {@code fallback} as the display name without making it part of the kit file. */
    public void useCatalogueDisplayName(String fallback) {
        this.displayName = fallback;
        this.displayNameFromCatalogue = true;
    }

    public boolean isFree() {
        return price <= 0;
    }

    public boolean isOneTime() {
        return !reBuyable;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasLevelRequirement() {
        return levelRequired > 0;
    }

    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }
}
