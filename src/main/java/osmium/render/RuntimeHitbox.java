package osmium.render;

import org.bukkit.entity.Interaction;
import osmium.model.HitboxPart;

public record RuntimeHitbox(HitboxPart blueprint, Interaction interaction) {}
