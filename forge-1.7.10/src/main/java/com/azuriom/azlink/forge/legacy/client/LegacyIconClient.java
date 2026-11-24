package com.azuriom.azlink.forge.legacy.client;

import com.azuriom.azlink.common.kits.manifest.icon.IconContentAddressing;
import com.azuriom.azlink.common.kits.manifest.render.*;
import com.azuriom.azlink.common.utils.VersionInfo;
import com.azuriom.azlink.forge.legacy.kits.LegacyKitItems;
import com.azuriom.azlink.forge.legacy.network.LegacyItemIcons;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;

@SideOnly(Side.CLIENT)
public final class LegacyIconClient {
    private final Queue<ItemIconRenderRequest> requests = new ConcurrentLinkedQueue<ItemIconRenderRequest>();
    private Object connection;
    public static void init() {
        LegacyIconClient worker = new LegacyIconClient();
        LegacyItemIcons.clientHandler(request -> { if (worker.requests.size() < 4) worker.requests.add(request); });
        FMLCommonHandler.instance().bus().register(worker);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) { connection = null; requests.clear(); return; }
        if (connection != mc.getNetHandler()) { connection = mc.getNetHandler(); requests.clear(); LegacyItemIcons.advertise(); }
        ItemIconRenderRequest request = requests.poll();
        if (request == null) return;
        List<IconRenderResult> icons = new ArrayList<IconRenderResult>();
        for (RenderItemPayload item : request.getItems()) {
            if (!ItemIconRenderProtocol.isSupported(request.getProtocolVersion())) { icons.add(IconRenderResult.unsupported(item.getRenderKey(), "protocol_mismatch")); continue; }
            try {
                ItemStack stack = LegacyKitItems.decode(item.getItemStackBytes());
                byte[] png = render(mc, stack);
                IconContentAddressing.validatePng(png);
                String name = request.getProtocolVersion() == ItemIconRenderProtocol.LOCALIZED_NAMES_VERSION
                        ? stack.getDisplayName() : null;
                icons.add(IconRenderResult.rendered(item.getRenderKey(), png, IconContentAddressing.sha256Hex(png), name));
            } catch (Exception failed) { icons.add(IconRenderResult.failed(item.getRenderKey(), "render_failed")); }
        }
        LegacyItemIcons.respond(new ItemIconRenderResponse(request.getRequestId(), request.getProtocolVersion(), VersionInfo.VERSION, icons));
    }
    private static byte[] render(Minecraft mc, ItemStack stack) throws Exception {
        if (!OpenGlHelper.isFramebufferEnabled()) throw new IllegalStateException("Framebuffer unavailable");
        int size = ItemIconRenderProtocol.ICON_SIZE;
        int previousFramebuffer = GL11.glGetInteger(0x8CA6), previousTexture = GL11.glGetInteger(0x84E0);
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        Framebuffer buffer = null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        try {
            buffer = new Framebuffer(size, size, true); buffer.bindFramebuffer(true);
            GL11.glClearColor(0,0,0,0); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity(); GL11.glOrtho(0,20,20,0,-1000,1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderHelper.enableGUIStandardItemLighting();
            RenderItem renderer = new RenderItem();
            renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 2, 2);
            renderer.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 2, 2, "");
            if (stack.stackSize > 1) {
                String count = Integer.toString(stack.stackSize);
                float scale = Math.min(1f, 18f / mc.fontRenderer.getStringWidth(count));
                GL11.glPushMatrix(); GL11.glScalef(scale, scale, 1); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST);
                mc.fontRenderer.drawStringWithShadow(count, (int) (19 / scale) - mc.fontRenderer.getStringWidth(count), (int) (11 / scale), 0xffffff);
                GL11.glPopMatrix();
            }
            ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
            GL11.glReadPixels(0,0,size,size,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            BufferedImage image = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            boolean visible = false;
            for (int y=0;y<size;y++) for(int x=0;x<size;x++) {
                int offset=(y*size+x)*4; int alpha=pixels.get(offset+3)&255;
                visible |= alpha != 0;
                image.setRGB(x,size-1-y,alpha<<24 | (pixels.get(offset)&255)<<16 | (pixels.get(offset+1)&255)<<8 | pixels.get(offset+2)&255);
            }
            if (!visible) throw new IllegalStateException("Blank icon");
            ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode);
            if (buffer != null) buffer.deleteFramebuffer();
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e,previousFramebuffer);
            OpenGlHelper.setActiveTexture(previousTexture); GL11.glPopAttrib();
        }
    }
}
