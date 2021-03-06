package com.elytradev.teckle.common.tile.retriever;

import com.elytradev.teckle.api.IWorldNetwork;
import com.elytradev.teckle.common.TeckleLog;
import com.elytradev.teckle.common.TeckleObjects;
import com.elytradev.teckle.common.block.BlockRetriever;
import com.elytradev.teckle.common.worldnetwork.common.WorldNetworkTraveller;
import com.elytradev.teckle.common.worldnetwork.common.node.NodeContainer;
import com.elytradev.teckle.common.worldnetwork.common.node.WorldNetworkEntryPoint;
import com.elytradev.teckle.common.worldnetwork.common.node.WorldNetworkNode;
import com.elytradev.teckle.common.worldnetwork.common.pathing.EndpointData;
import com.elytradev.teckle.common.worldnetwork.common.pathing.PathNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.TreeMultiset;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.*;

public class NetworkTileRetrieverInput extends NetworkTileRetrieverBase {

    protected TreeMultiset<PathNode> sourceNodes = TreeMultiset.create(Comparator.comparingInt(o -> o.cost));

    public NetworkTileRetrieverInput(World world, BlockPos pos, EnumFacing face) {
        super(world, pos, face);
    }

    public NetworkTileRetrieverInput(TileRetriever retriever) {
        super(retriever.getWorld(), retriever.getPos(), retriever.getFacing().getOpposite());

        this.filterData = retriever.filterData;
        this.bufferData = retriever.bufferData;
        this.filterID = retriever.filterID;
        this.bufferID = retriever.bufferID;
    }

    @Override
    public WorldNetworkNode createNode(IWorldNetwork network, BlockPos pos) {
        return new RetrieverEndpoint(network, pos, getCapabilityFace());
    }

    @Override
    public void setNode(WorldNetworkNode node) {
        if (!Objects.equals(node, this.getNode())) {
            super.setNode(node);
            if (getNode() != null) {
                IWorldNetwork network = getNode().getNetwork();
                for (NodeContainer nodeContainer : network.getNodes()) {
                    if (nodeContainer.getNode().isEndpoint()) {
                        onNodeAdded(nodeContainer.getNode());
                    }
                }
            }
        } else {
            super.setNode(node);
        }
    }

    @Override
    public boolean canAcceptTraveller(WorldNetworkTraveller traveller, EnumFacing from) {
        return from.equals(getCapabilityFace()) && sourceNodes.stream()
                .anyMatch(pN -> Objects.equals(pN.realNode.getPosition(), traveller.getEntryPoint().getPosition()));
    }

    @Override
    public EnumFacing getCapabilityFace() {
        if (getWorld() != null && getWorld().isBlockLoaded(getPos())) {
            IBlockState thisState = getWorld().getBlockState(getPos());
            if (Objects.equals(thisState.getBlock(), TeckleObjects.blockRetriever)) {
                setCapabilityFace(thisState.getValue(BlockRetriever.FACING).getOpposite());
            }
        }

        return super.getCapabilityFace();
    }

    @Override
    public boolean listenToNetworkChange() {
        return true;
    }

    @Override
    public void onNodeAdded(WorldNetworkNode addedNode) {
        // Only add if it's not already present, and has IO for transfer of items.
        boolean nodePresent = sourceNodes.stream().anyMatch(pathNode -> pathNode.realNode.equals(addedNode));
        TeckleLog.info("Source node added {} {}", addedNode, nodePresent);
        if (addedNode.isEndpoint()) {
            List<PathNode> nodeStack = new ArrayList<>();
            List<BlockPos> iteratedPositions = new ArrayList<>();
            HashMap<BlockPos, HashMap<EnumFacing, EndpointData>> endpoints = new HashMap<>();
            IWorldNetwork network = getNode().getNetwork();

            nodeStack.add(new PathNode(null, getNode(), null));
            while (!nodeStack.isEmpty()) {
                PathNode pathNode = nodeStack.remove(nodeStack.size() - 1);
                for (EnumFacing direction : EnumFacing.VALUES) {
                    BlockPos neighbourPos = pathNode.realNode.getPosition().add(direction.getDirectionVec());
                    if (!network.isNodePresent(neighbourPos, direction.getOpposite()) ||
                            iteratedPositions.contains(neighbourPos) ||
                            (endpoints.containsKey(neighbourPos) &&
                                    endpoints.get(neighbourPos).containsKey(direction.getOpposite()))) {
                        continue;
                    }
                    WorldNetworkNode neighbourNode = network.getNode(neighbourPos, direction.getOpposite());
                    if (Objects.equals(addedNode, neighbourNode)) {
                        if (!endpoints.containsKey(neighbourPos)) {
                            endpoints.put(neighbourPos, new HashMap<>());
                        }
                        PathNode pN = new PathNode(pathNode, network.getNode(neighbourPos, direction.getOpposite()), direction.getOpposite());
                        endpoints.get(neighbourPos).put(direction.getOpposite(), new EndpointData(pN, direction.getOpposite()));

                        TeckleLog.info("Source node added PASS {}", neighbourNode);
                    } else if (network.getNode(neighbourPos, direction.getOpposite()).canConnectTo(direction.getOpposite())) {
                        nodeStack.add(new PathNode(pathNode, network.getNode(neighbourPos, direction.getOpposite()), direction.getOpposite()));
                        iteratedPositions.add(neighbourPos);
                    }
                }
            }

            for (Map.Entry<BlockPos, HashMap<EnumFacing, EndpointData>> entry : endpoints.entrySet()) {
                for (EndpointData endpointData : entry.getValue().values()) {
                    if (sourceNodes.stream().noneMatch(pathNode -> pathNode.realNode.getPosition().equals(endpointData.pos)
                            && pathNode.from.equals(endpointData.node.from))) {
                        sourceNodes.add(endpointData.node);
                    }
                }
            }

            for (PathNode sourceNode : sourceNodes) {
                TeckleLog.info("SN " + sourceNode.realNode.toString());
            }
        }
    }

    @Override
    public void onNodeRemoved(WorldNetworkNode removedNode) {
        // Remove the node if it's known to us.
        sourceNodes.removeIf(pN -> pN.realNode.equals(removedNode) || pN.realNode.getPosition().equals(removedNode.getPosition()));
    }

    @Override
    public boolean isValidNetworkMember(IWorldNetwork network, EnumFacing side) {
        return Objects.equals(side, getCapabilityFace());
    }

    @Override
    public boolean canConnectTo(EnumFacing side) {
        return Objects.equals(side, getCapabilityFace());
    }

    private boolean isValidSourceNode(BlockPos position, EnumFacing direction) {
        direction = direction.getOpposite();

        TileEntity tileEntity = getWorld().getTileEntity(position);
        return tileEntity != null && tileEntity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
    }

    public ItemStack acceptTraveller(WorldNetworkTraveller traveller) {
        if (sourceNodes.stream().anyMatch(pN ->
                Objects.equals(pN.realNode.getPosition(), traveller.getEntryPoint().getPosition()))) {
            ImmutableMap<String, NBTBase> additionalData = getColour() == null ? ImmutableMap.of()
                    : ImmutableMap.of("colour", new NBTTagInt(getColour().getMetadata()));
            ItemStack remainder = getOutputTile().getNetworkAssistant(ItemStack.class).insertData(
                    (WorldNetworkEntryPoint) getOutputTile().getNode(), getPos().offset(getCapabilityFace().getOpposite()),
                    new ItemStack(traveller.data.getCompoundTag("stack")), additionalData, false, false);
            if (!remainder.isEmpty())
                remainder = bufferData.getHandler().insertItem(remainder, false);
            setTriggered();
            return remainder;
        } else {
            return new ItemStack(traveller.data.getCompoundTag("stack"));
        }
    }
}
