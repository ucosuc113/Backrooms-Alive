package com.glados.backrooms.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record MemoryConnector(MemoryConnectorType type, BlockPos relativePos, Direction direction) {
}
