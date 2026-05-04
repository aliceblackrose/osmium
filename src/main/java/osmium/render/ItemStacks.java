package osmium.render;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public final class ItemStacks {
  private static final String DEFAULT_ITEM_MODEL_NAMESPACE = "osmium";

  private static final ItemFlag[] ALL_ITEM_FLAGS = ItemFlag.values();

  private ItemStacks() {}

  public static ItemStack displayItem(Material material, String itemModelKey, int customModelData) {
    ItemStack itemStack = new ItemStack(material);
    ItemMeta itemMeta = itemStack.getItemMeta();

    if (itemMeta == null) {
      return itemStack;
    }

    setCustomModelData(itemMeta, customModelData);
    itemMeta.setItemModel(namespacedKey(itemModelKey));
    itemMeta.addItemFlags(ALL_ITEM_FLAGS);
    itemStack.setItemMeta(itemMeta);

    return itemStack;
  }

  private static void setCustomModelData(ItemMeta itemMeta, int customModelData) {
    CustomModelDataComponent component = itemMeta.getCustomModelDataComponent();
    component.setFloats(List.of((float) customModelData));
    itemMeta.setCustomModelDataComponent(component);
  }

  private static NamespacedKey namespacedKey(String rawItemModelKey) {
    int namespaceSeparatorIndex = rawItemModelKey.indexOf(':');

    if (namespaceSeparatorIndex > 0) {
      return new NamespacedKey(
          rawItemModelKey.substring(0, namespaceSeparatorIndex),
          rawItemModelKey.substring(namespaceSeparatorIndex + 1));
    }

    return new NamespacedKey(DEFAULT_ITEM_MODEL_NAMESPACE, rawItemModelKey);
  }
}
