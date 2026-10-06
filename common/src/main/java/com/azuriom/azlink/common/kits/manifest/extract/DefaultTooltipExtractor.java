package com.azuriom.azlink.common.kits.manifest.extract;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;

import java.util.ArrayList;
import java.util.List;

/**
 * Truncates and cleans tooltip lines to site limits.
 */
public final class DefaultTooltipExtractor implements TooltipExtractor {

    @Override
    public List<String> extract(ItemStackSnapshot snapshot) {
        List<String> out = new ArrayList<String>();
        if (snapshot == null || snapshot.getTooltipLines() == null) {
            return out;
        }
        for (String line : snapshot.getTooltipLines()) {
            if (out.size() >= KitManifestLimits.MAX_TOOLTIP_LINES) {
                break;
            }
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH) {
                trimmed = trimmed.substring(0, KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH);
            }
            out.add(trimmed);
        }
        return out;
    }
}
