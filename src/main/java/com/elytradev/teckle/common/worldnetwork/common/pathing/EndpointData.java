package com.elytradev.teckle.common.worldnetwork.common.pathing;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * Stores an endpoint position as well as the side that endpoint is being injected to.
 */
public class EndpointData {
    public EnumFacing side;
    public BlockPos pos;
    public PathNode node;
    public int cost;

    public EndpointData(PathNode node, EnumFacing side) {
        this.side = side;
        this.node = node;

        this.cost = node.cost;
        this.pos = node.realNode.position;
    }

    @Override
    public String toString() {
        return "EndpointData{" +
                "side=" + side +
                ", pos=" + pos +
                ", cost=" + cost +
                ", node=" + node +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EndpointData that = (EndpointData) o;
        return side == that.side &&
                Objects.equals(pos, that.pos);
    }
}