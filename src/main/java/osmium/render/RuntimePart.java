package osmium.render;

import org.bukkit.entity.ItemDisplay;
import osmium.model.RenderPart;

public record RuntimePart(RenderPart blueprint, ItemDisplay display) {}
