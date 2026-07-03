package com.glados.backrooms.placement;

import com.glados.backrooms.analysis.WallColumn;
import com.glados.backrooms.analysis.WallPrototype;

/** Helper puro: proyecta un WallPrototype sobre un segmento de longitud variable. */
final class WallPatternMapper {

    static WallColumn mapColumn(WallPrototype proto, int pos, int segLen) {
        if (proto == null) return neutralColumn();
        int refLen = proto.referenceLength();
        if (refLen == 0) return neutralColumn();

        // extremos
        if (pos < 2) {
            var ends = proto.endColumnsLeft();
            return ends.get(Math.min(pos, ends.size()-1));
        }
        if (pos >= segLen - 2) {
            int idx = segLen - pos - 1;
            var ends = proto.endColumnsRight();
            return ends.get(Math.min(idx, ends.size()-1));
        }

        var tile = proto.tileableRange();
        if (tile.isEmpty()) return neutralColumn();

        int tileableLen = tile.size();
        int innerPos = pos - 2;
        int innerLen = segLen - 4;
        if (innerLen <= 0) innerLen = 1; // avoid div by zero

        if (innerLen >= tileableLen) {
            int idx = innerPos % tileableLen;
            return tile.get(idx);
        } else {
            // scale
            int idx = (int) ((long) innerPos * tileableLen / innerLen);
            idx = Math.min(idx, tileableLen - 1);
            return tile.get(idx);
        }
    }

    static WallColumn neutralColumn() {
        return new WallColumn(new net.minecraft.world.level.block.state.BlockState[]{
            com.glados.backrooms.registry.ModBlocks.BACK_WALL.get().defaultBlockState(),
            com.glados.backrooms.registry.ModBlocks.BACK_WALL.get().defaultBlockState(),
            com.glados.backrooms.registry.ModBlocks.BACK_WALL.get().defaultBlockState(),
            com.glados.backrooms.registry.ModBlocks.BACK_WALL.get().defaultBlockState()
        }, false);
    }
}
