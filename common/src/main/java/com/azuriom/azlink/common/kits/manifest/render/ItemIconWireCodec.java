package com.azuriom.azlink.common.kits.manifest.render;

import com.azuriom.azlink.common.kits.manifest.KitManifestLimits;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Bounded binary payloads shared by the Forge 1.20.1 and 1.7.10 channels. No site credentials. */
public final class ItemIconWireCodec {
    public static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private ItemIconWireCodec() {}

    public static byte[] capabilities(ClientCapabilities value) {
        return write(out -> {
            out.writeInt(value.getProtocolVersion()); out.writeBoolean(value.supportsItemIconRendering());
            text(out, value.getRendererVersion(), 100); out.writeInt(value.getSupportedIconSize());
            text(out, value.getSupportedImageFormat(), 16);
        });
    }
    public static ClientCapabilities capabilities(byte[] bytes) {
        return read(bytes, in -> new ClientCapabilities(in.readInt(), in.readBoolean(), text(in,100), in.readInt(), text(in,16)));
    }
    public static byte[] request(ItemIconRenderRequest value) {
        return write(out -> {
            uuid(out,value.getRequestId()); out.writeInt(value.getProtocolVersion()); out.writeInt(value.getIconSize());
            count(value.getItems().size()); out.writeInt(value.getItems().size());
            for(RenderItemPayload item:value.getItems()) {
                text(out,item.getRenderKey(),128); out.writeInt(item.getSlot()); blob(out,item.getItemStackBytes(),ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES);
            }
        });
    }
    public static ItemIconRenderRequest request(byte[] bytes) {
        return read(bytes, in -> {
            UUID id=uuid(in); int version=in.readInt(), size=in.readInt(), count=in.readInt(); count(count);
            if(size!=ItemIconRenderProtocol.ICON_SIZE) throw new IOException("unsupported icon size");
            List<RenderItemPayload> items=new ArrayList<RenderItemPayload>();
            for(int i=0;i<count;i++) items.add(new RenderItemPayload(text(in,128),in.readInt(),blob(in,ItemIconRenderProtocol.MAX_ITEM_STACK_BYTES)));
            return new ItemIconRenderRequest(id,version,size,items);
        });
    }
    public static byte[] response(ItemIconRenderResponse value) {
        return write(out -> {
            uuid(out,value.getRequestId()); out.writeInt(value.getProtocolVersion()); text(out,value.getRendererVersion(),100);
            count(value.getIcons().size()); out.writeInt(value.getIcons().size());
            for(IconRenderResult icon:value.getIcons()) {
                text(out,icon.getRenderKey(),128); out.writeByte(icon.getStatus().ordinal());
                if(icon.getStatus()==IconRenderResult.Status.RENDERED) {
                    blob(out,icon.getPngBytes(),ItemIconRenderProtocol.MAX_PNG_BYTES); text(out,icon.getClientSha256(),64);
                    if (ItemIconRenderProtocol.hasLocalizedNames(value.getProtocolVersion())) {
                        text(out, icon.getDisplayName(), KitManifestLimits.MAX_DISPLAY_NAME);
                    }
                    if (ItemIconRenderProtocol.hasTooltips(value.getProtocolVersion())) {
                        out.writeInt(icon.getTooltip().size());
                        for (String line : icon.getTooltip()) {
                            text(out, line, KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH);
                        }
                    }
                } else text(out,icon.getErrorCode(),160);
            }
        });
    }
    public static ItemIconRenderResponse response(byte[] bytes) {
        return read(bytes, in -> {
            UUID id=uuid(in); int version=in.readInt(); String renderer=text(in,100); int count=in.readInt(); count(count);
            List<IconRenderResult> icons=new ArrayList<IconRenderResult>();
            for(int i=0;i<count;i++) {
                String key=text(in,128); int status=in.readUnsignedByte();
                if(status==IconRenderResult.Status.RENDERED.ordinal()) {
                    byte[] png=blob(in,ItemIconRenderProtocol.MAX_PNG_BYTES); String hash=text(in,64);
                    String name = ItemIconRenderProtocol.hasLocalizedNames(version)
                            ? text(in, KitManifestLimits.MAX_DISPLAY_NAME) : null;
                    List<String> tooltip = ItemIconRenderProtocol.hasTooltips(version)
                            ? tooltip(in) : Collections.<String>emptyList();
                    icons.add(IconRenderResult.rendered(key, png, hash, name, tooltip));
                }
                else if(status==IconRenderResult.Status.FAILED.ordinal()) icons.add(IconRenderResult.failed(key,text(in,160)));
                else if(status==IconRenderResult.Status.UNSUPPORTED.ordinal()) icons.add(IconRenderResult.unsupported(key,text(in,160)));
                else throw new IOException("invalid icon status");
            }
            return new ItemIconRenderResponse(id,version,renderer,icons);
        });
    }
    private static List<String> tooltip(DataInputStream in) throws IOException {
        int before = in.available();
        int lines = in.readInt();
        if (lines < 0 || lines > KitManifestLimits.MAX_TOOLTIP_LINES) {
            throw new IOException("invalid tooltip line count");
        }
        List<String> tooltip = new ArrayList<String>(lines);
        for (int i = 0; i < lines; i++) {
            tooltip.add(text(in, KitManifestLimits.MAX_TOOLTIP_LINE_LENGTH));
            if (before - in.available() > ItemIconRenderProtocol.MAX_TOOLTIP_WIRE_BYTES) {
                throw new IOException("tooltip payload too large");
            }
        }
        return tooltip;
    }

    private static void count(int n) throws IOException { if(n<0 || n>ItemIconRenderProtocol.MAX_ITEMS_PER_REQUEST) throw new IOException("invalid item count"); }
    private static void uuid(DataOutputStream out,UUID id) throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in) throws IOException {return new UUID(in.readLong(),in.readLong());}
    private static void text(DataOutputStream out,String value,int max) throws IOException {
        String text = value == null ? "" : value;
        if (text.length() > max) throw new IOException("text too long");
        blob(out,text.getBytes(StandardCharsets.UTF_8),max*4);
    }
    private static String text(DataInputStream in,int max) throws IOException {
        String value=new String(blob(in,max*4),StandardCharsets.UTF_8);
        if(value.length()>max) throw new IOException("text too long"); return value;
    }
    private static void blob(DataOutputStream out,byte[] value,int max) throws IOException {
        if(value==null || value.length>max) throw new IOException("payload too large"); out.writeInt(value.length);out.write(value);
    }
    private static byte[] blob(DataInputStream in,int max) throws IOException {
        int len=in.readInt();if(len<0 || len>max || len>in.available()) throw new IOException("invalid payload length");
        byte[] bytes=new byte[len];in.readFully(bytes);return bytes;
    }
    private interface Writer {void run(DataOutputStream out) throws IOException;}
    private interface Reader<T> {T run(DataInputStream in) throws IOException;}
    private static byte[] write(Writer writer) {
        try {ByteArrayOutputStream bytes=new ByteArrayOutputStream();writer.run(new DataOutputStream(bytes));
            if(bytes.size()>MAX_PACKET_BYTES)throw new IOException("packet too large");return bytes.toByteArray();
        }catch(IOException e){throw new IllegalArgumentException("Invalid icon packet",e);}
    }
    private static <T>T read(byte[] bytes,Reader<T> reader) {
        if(bytes==null || bytes.length>MAX_PACKET_BYTES)throw new IllegalArgumentException("packet too large");
        try {DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));T value=reader.run(in);
            if(in.available()!=0)throw new IOException("trailing packet data");return value;
        }catch(IOException e){throw new IllegalArgumentException("Invalid icon packet",e);}
    }
}
