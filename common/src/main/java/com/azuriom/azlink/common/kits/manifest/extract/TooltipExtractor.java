package com.azuriom.azlink.common.kits.manifest.extract;

import java.util.List;

/**
 * Extracts / normalizes tooltip lines from an {@link ItemStackSnapshot}.
 */
public interface TooltipExtractor {

    List<String> extract(ItemStackSnapshot snapshot);
}
