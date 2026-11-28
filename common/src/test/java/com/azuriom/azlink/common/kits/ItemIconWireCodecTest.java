package com.azuriom.azlink.common.kits;

import com.azuriom.azlink.common.kits.manifest.render.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemIconWireCodecTest {
    @Test void oneNative64PixelPngFitsLegacyAndForgeServerboundPacketLimit() throws Exception {
        int size = ItemIconRenderProtocol.ICON_SIZE;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size,size,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) image.setRGB(x,y,random.nextInt());
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image,"png",bytes);
        ItemIconRenderResponse response = new ItemIconRenderResponse(UUID.randomUUID(), ItemIconRenderProtocol.VERSION,"test",
            Collections.singletonList(IconRenderResult.rendered("1234567890abcdef",bytes.toByteArray(),"1234567890abcdef")));
        assertTrue(ItemIconWireCodec.response(response).length + 64 < 32767, "Leave room for the channel/message wrapper");
        List<String> tooltip = Collections.nCopies(32, String.join("", Collections.nCopies(500, "Ж")));
        ItemIconRenderResponse withTooltip = new ItemIconRenderResponse(UUID.randomUUID(),
                ItemIconRenderProtocol.TOOLTIPS_VERSION, "test",
                Collections.singletonList(IconRenderResult.rendered("key", bytes.toByteArray(), "hash", "Броня", tooltip)));
        assertTrue(ItemIconWireCodec.response(withTooltip).length + 64 < 32767);
    }

    @Test void tooltipsAreOptionalForOlderRenderersAndRetainFormattingInVersionThree() {
        List<String> tooltip = Arrays.asList("§x§e§3§b§a§4§5Редкая броня", "§b+8 брони", "Гнездо самоцвета");
        IconRenderResult result = IconRenderResult.rendered("key", new byte[]{1, 2}, "hash", "Броня", tooltip);
        for (int version : new int[]{ItemIconRenderProtocol.LOCALIZED_NAMES_VERSION, ItemIconRenderProtocol.TOOLTIPS_VERSION}) {
            ItemIconRenderResponse response = new ItemIconRenderResponse(UUID.randomUUID(), version, "test",
                    Collections.singletonList(result));
            IconRenderResult decoded = ItemIconWireCodec.response(ItemIconWireCodec.response(response)).getIcons().get(0);
            assertEquals("Броня", decoded.getDisplayName());
            assertEquals(version == ItemIconRenderProtocol.TOOLTIPS_VERSION ? tooltip : Collections.emptyList(),
                    decoded.getTooltip());
        }
    }
    @Test void roundTripsBinaryNbtAndAuthenticatedRequestIdentity() {
        UUID id=UUID.randomUUID(); byte[] nbt={0,1,-1,34,0};
        ItemIconRenderRequest original=new ItemIconRenderRequest(id,ItemIconRenderProtocol.VERSION,ItemIconRenderProtocol.ICON_SIZE,
                Collections.singletonList(new RenderItemPayload("key",7,nbt)));
        ItemIconRenderRequest decoded=ItemIconWireCodec.request(ItemIconWireCodec.request(original));
        assertEquals(id,decoded.getRequestId()); assertEquals(7,decoded.getItems().get(0).getSlot());
        assertArrayEquals(nbt,decoded.getItems().get(0).getItemStackBytes());
        assertEquals(ClientCapabilities.v1("test"),ItemIconWireCodec.capabilities(ItemIconWireCodec.capabilities(ClientCapabilities.v1("test"))));
    }
    @Test void roundTripsAllResultStatuses() {
        ItemIconRenderResponse original=new ItemIconRenderResponse(UUID.randomUUID(),ItemIconRenderProtocol.VERSION,"test",
            Arrays.asList(IconRenderResult.rendered("ok",new byte[]{1,2},"abc"), IconRenderResult.failed("bad","error"),IconRenderResult.unsupported("old","version")));
        ItemIconRenderResponse decoded=ItemIconWireCodec.response(ItemIconWireCodec.response(original));
        assertEquals(original.getRequestId(),decoded.getRequestId());
        assertArrayEquals(new byte[]{1,2},decoded.getIcons().get(0).getPngBytes());
        assertEquals(IconRenderResult.Status.FAILED,decoded.getIcons().get(1).getStatus());
        assertEquals(IconRenderResult.Status.UNSUPPORTED,decoded.getIcons().get(2).getStatus());
    }
    @Test void rejectsTruncationTrailingDataAndOversizedBlobsBeforeAllocation() {
        byte[] valid=ItemIconWireCodec.capabilities(ClientCapabilities.v1("test"));
        assertThrows(IllegalArgumentException.class,()->ItemIconWireCodec.capabilities(Arrays.copyOf(valid,valid.length-1)));
        assertThrows(IllegalArgumentException.class,()->ItemIconWireCodec.capabilities(Arrays.copyOf(valid,valid.length+1)));
        byte[] oversized=valid.clone(); oversized[5]=0x7f; oversized[6]=-1; oversized[7]=-1; oversized[8]=-1;
        assertThrows(IllegalArgumentException.class,()->ItemIconWireCodec.capabilities(oversized));
        assertThrows(IllegalArgumentException.class,()->ItemIconWireCodec.request(new byte[ItemIconWireCodec.MAX_PACKET_BYTES+1]));
    }
}
