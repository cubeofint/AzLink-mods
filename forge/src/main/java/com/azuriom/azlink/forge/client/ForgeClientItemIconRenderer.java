package com.azuriom.azlink.forge.client;

import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;
import com.azuriom.azlink.common.kits.manifest.render.IconRenderResult;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderProtocol;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderRequest;
import com.azuriom.azlink.common.kits.manifest.render.ItemIconRenderResponse;
import com.azuriom.azlink.common.kits.manifest.render.RenderItemPayload;
import com.azuriom.azlink.common.utils.VersionInfo;
import com.azuriom.azlink.forge.network.ForgeItemIcons;
import com.azuriom.azlink.forge.kits.ForgeKitItems;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Client-only rendering worker. Never talks to main-site / never holds Azuriom-Link-Token.
 */
public final class ForgeClientItemIconRenderer {

    public void handleRequest(ItemIconRenderRequest request) {
        Minecraft.getInstance().execute(() -> processOnClientThread(request));
    }

    private void processOnClientThread(ItemIconRenderRequest request) {
        List<IconRenderResult> results = new ArrayList<>();
        if (!ItemIconRenderProtocol.isSupported(request.getProtocolVersion())) {
            for (RenderItemPayload item : request.getItems()) {
                results.add(IconRenderResult.unsupported(item.getRenderKey(), "protocol_mismatch"));
            }
            send(request, results);
            return;
        }

        for (RenderItemPayload item : request.getItems()) {
            try {
                ItemStack stack = ForgeKitItems.decode(item.getItemStackBytes());
                if (stack.isEmpty()) {
                    results.add(IconRenderResult.failed(item.getRenderKey(), "empty_stack"));
                    continue;
                }
                byte[] png = renderItemToPng(stack, request.getIconSize());
                String hash = IconContentAddressing.sha256Hex(png);
                String name = ItemIconRenderProtocol.hasLocalizedNames(request.getProtocolVersion())
                        ? stack.getHoverName().getString() : null;
                List<String> tooltip = ItemIconRenderProtocol.hasTooltips(request.getProtocolVersion())
                        ? captureTooltip(stack) : List.of();
                results.add(IconRenderResult.rendered(item.getRenderKey(), png, hash, name, tooltip));
            } catch (Exception e) {
                results.add(IconRenderResult.failed(item.getRenderKey(),
                        e.getMessage() == null ? "render_failed" : truncate(e.getMessage(), 60)));
            }
        }
        send(request, results);
    }

    private static List<String> captureTooltip(ItemStack stack) {
        try {
            return stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL).stream()
                    .map(ForgeClientItemIconRenderer::tooltipLine)
                    .toList();
        } catch (RuntimeException ignored) {
            // A mod's tooltip hook may fail. The item name and icon can still be exported.
            return List.of();
        }
    }

    private static String tooltipLine(Component component) {
        StringBuilder line = new StringBuilder();
        component.visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            line.append("§r");
            if (style.getColor() != null) {
                line.append("§x");
                String hex = String.format(Locale.ROOT, "%06x", style.getColor().getValue());
                for (int i = 0; i < hex.length(); i++) {
                    line.append('§').append(hex.charAt(i));
                }
            }
            if (style.isBold()) line.append("§l");
            if (style.isItalic()) line.append("§o");
            if (style.isUnderlined()) line.append("§n");
            if (style.isStrikethrough()) line.append("§m");
            line.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return line.toString();
    }

    private void send(ItemIconRenderRequest request, List<IconRenderResult> results) {
        ItemIconRenderResponse response = new ItemIconRenderResponse(
                request.getRequestId(),
                request.getProtocolVersion(),
                VersionInfo.VERSION,
                results
        );
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null || !ForgeItemIcons.present(connection.getConnection())) {
            return;
        }
        ForgeItemIcons.respond(response);
    }

    private byte[] renderItemToPng(ItemStack stack, int size) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        TextureTarget target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        boolean projectionBackedUp = false;
        PoseStack modelView = RenderSystem.getModelViewStack();
        boolean modelViewPushed = false;
        try {
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.viewport(0, 0, size, size);
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;
            // GUI-style ortho: y grows downward (same as GuiGraphics)
            Matrix4f projection = new Matrix4f().setOrtho(0f, size, size, 0f, 1000f, 3000f);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

            modelView.pushPose();
            modelViewPushed = true;
            modelView.setIdentity();
            modelView.translate(0f, 0f, -2000f);
            RenderSystem.applyModelViewMatrix();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Lighting.setupForFlatItems();

            GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            graphics.pose().pushPose();
            // Item GUI models are authored for 16×16 slots
            float scale = size / 16.0f;
            graphics.pose().scale(scale, scale, scale);
            graphics.renderItem(stack, 0, 0);
            graphics.renderItemDecorations(mc.font, stack, 0, 0);
            graphics.pose().popPose();
            graphics.flush();

            modelView.popPose();
            modelViewPushed = false;
            RenderSystem.applyModelViewMatrix();

            NativeImage image = new NativeImage(size, size, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();

            if (isFullyTransparent(image)) {
                image.close();
                throw new IllegalStateException("blank_icon");
            }

            byte[] png = encodePng(image, size);
            image.close();
            IconContentAddressing.validatePng(png);
            return png;
        } finally {
            if (modelViewPushed) {
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
            }
            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }
            if (main != null) {
                main.bindWrite(true);
                RenderSystem.viewport(0, 0, main.width, main.height);
            }
            target.destroyBuffers();
        }
    }

    private static boolean isFullyTransparent(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] encodePng(NativeImage image, int size) throws Exception {
        // NativeImage stores ABGR in the int returned by getPixelRGBA (Mojang naming).
        java.awt.image.BufferedImage buffered =
                new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >>> 24) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int r = abgr & 0xFF;
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                buffered.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(buffered, "png", out)) {
            throw new IllegalStateException("png_encode_failed");
        }
        return out.toByteArray();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
