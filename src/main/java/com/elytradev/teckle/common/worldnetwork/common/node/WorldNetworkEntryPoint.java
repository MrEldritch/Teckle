package com.elytradev.teckle.common.worldnetwork.common.node;

import com.elytradev.teckle.api.IWorldNetwork;
import com.elytradev.teckle.common.network.TravellerDataMessage;
import com.elytradev.teckle.common.tile.base.TileNetworkEntrypoint;
import com.elytradev.teckle.common.worldnetwork.common.WorldNetworkTraveller;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * A node used to add travellers to a network, handles initial endpoint finding, as well as finding new endpoints when one fails.
 */
public class WorldNetworkEntryPoint extends WorldNetworkNode {

    public WorldNetworkEndpoint endpoint = new WorldNetworkEndpoint(network, position) {
        @Override
        public boolean inject(WorldNetworkTraveller traveller, EnumFacing from) {
            if (network.getWorld().getTileEntity(position) != null && network.getWorld().getTileEntity(position) instanceof TileNetworkEntrypoint) {
                ((TileNetworkEntrypoint) network.getWorld().getTileEntity(position)).acceptReturn(traveller, from);
            }
            return true;
        }
    };
    private EnumFacing facing = EnumFacing.DOWN;

    public WorldNetworkEntryPoint(IWorldNetwork network, BlockPos position, EnumFacing facing) {
        this.network = network;
        this.position = position;
        this.facing = facing;

        this.endpoint.position = this.position;
        this.endpoint.network = this.network;
    }

    public WorldNetworkTraveller addTraveller(NBTTagCompound data) {
        WorldNetworkTraveller traveller = new WorldNetworkTraveller(this, data);
        if (traveller.genInitialPath()) {
            network.registerTraveller(traveller, false);
            new TravellerDataMessage(TravellerDataMessage.Action.REGISTER, traveller, traveller.currentNode.position,
                    traveller.previousNode.position).sendToAllWatching(network.getWorld(), position);
            return traveller;
        }

        return WorldNetworkTraveller.NONE;
    }

    public EnumFacing getFacing() {
        return facing;
    }

}
