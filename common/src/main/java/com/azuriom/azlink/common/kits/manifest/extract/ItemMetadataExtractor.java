package com.azuriom.azlink.common.kits.manifest.extract;

import com.azuriom.azlink.common.kits.manifest.model.ItemIconRef;
import com.azuriom.azlink.common.kits.manifest.model.ManifestItem;

/**
 * Maps an {@link ItemStackSnapshot} into a wire {@link ManifestItem} (without uploading icons).
 */
public interface ItemMetadataExtractor {

    ManifestItem extract(ItemStackSnapshot snapshot, ItemIconRef icon);
}
