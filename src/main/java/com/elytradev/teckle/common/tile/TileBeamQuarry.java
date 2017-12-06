package com.elytradev.teckle.common.tile;

import com.elytradev.teckle.client.gui.GuiBeamQuarry;
import com.elytradev.teckle.common.TeckleLog;
import com.elytradev.teckle.common.block.BlockBeamQuarry;
import com.elytradev.teckle.common.container.ContainerBeamQuarry;
import com.elytradev.teckle.common.network.messages.clientbound.TileUpdateMessage;
import com.elytradev.teckle.common.tile.base.IElementProvider;
import com.elytradev.teckle.common.tile.inv.AdvancedItemStackHandler;
import com.elytradev.teckle.common.tile.inv.ItemStream;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TileBeamQuarry extends TileEntity implements ITickable, IElementProvider {

    private BlockPos min = pos, max = pos, cursor = pos;
    public int left, right, forward;
    public AdvancedItemStackHandler buffer = new AdvancedItemStackHandler(25);
    public AdvancedItemStackHandler junkSupply = new AdvancedItemStackHandler(12);
    public AdvancedItemStackHandler junkTypes = new AdvancedItemStackHandler(6);

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        this.min = BlockPos.fromLong(tag.getLong("min"));
        this.max = BlockPos.fromLong(tag.getLong("max"));
        this.cursor = BlockPos.fromLong(tag.getLong("cursor"));
        this.left = tag.getInteger("left");
        this.right = tag.getInteger("right");
        this.forward = tag.getInteger("forward");

        this.buffer.deserializeNBT(tag.getCompoundTag("buffer"));
        this.junkSupply.deserializeNBT(tag.getCompoundTag("junkSupply"));
        this.junkTypes.deserializeNBT(tag.getCompoundTag("junkTypes"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setLong("min", this.min.toLong());
        tag.setLong("max", this.max.toLong());
        tag.setLong("cursor", this.cursor.toLong());
        tag.setInteger("left", left);
        tag.setInteger("right", right);
        tag.setInteger("forward", forward);

        tag.setTag("buffer", this.buffer.serializeNBT());
        tag.setTag("junkSupply", this.junkSupply.serializeNBT());
        tag.setTag("junkTypes", this.junkTypes.serializeNBT());

        return super.writeToNBT(tag);
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock()
                || newState.getValue(BlockBeamQuarry.FACING) != oldState.getValue(BlockBeamQuarry.FACING);
    }

    @Override
    public void update() {
        //TODO: Power consumption and cooldown.
        if (isActive()) {
            // TODO: Remove, just for testing.
            if (world.isRemote || world.getTotalWorldTime() % 5 != 0)
                return;

            if (cursor == pos) {
                cursor = min;
            }

            // Check the current cursor position for validity.
            IBlockState cursorState = world.getBlockState(cursor);
            if (!isStateValid(cursorState)) {
                int xRange = Math.abs(min.getX() - max.getX()) + 1;
                int zRange = Math.abs(min.getZ() - max.getZ()) + 1;
                for (int y = cursor.getY(); y > 0 && !isStateValid(cursorState); y--) {
                    for (int x = 0; x < xRange && !isStateValid(cursorState); x++) {
                        for (int z = 0; z < zRange && !isStateValid(cursorState); z++) {
                            cursor = new BlockPos(min.getX() + x, y, min.getZ() + z);
                            if (isStateValid(world.getBlockState(cursor))) {
                                cursorState = world.getBlockState(cursor);
                                break;
                            }
                        }
                    }
                }
            }
            // Mine the current cursor position.
            if (isStateValid(cursorState)) {
                AxisAlignedBB dropBox = new AxisAlignedBB(cursor.getX(), cursor.getY(), cursor.getZ(),
                        cursor.getX(), cursor.getY(), cursor.getZ());
                dropBox = dropBox.expand(1.5, 1.5, 1.5);
                world.destroyBlock(pos, true);
                List<EntityItem> entityItems = world.getEntitiesWithinAABB(EntityItem.class, dropBox);
                List<ItemStack> items = entityItems.stream().map(EntityItem::getItem).collect(Collectors.toList());
                entityItems.forEach(Entity::setDead);
                for (ItemStack item : items) {
                    Stream<ItemStack> junkStream = ItemStream.createItemStream(junkTypes);
                    ItemStack finalItem = item;
                    if (junkStream.anyMatch(j -> ItemHandlerHelper.canItemStacksStack(finalItem, j))) {
                        item = junkSupply.insertItem(item, false);
                    }
                    item = buffer.insertItem(item, false);
                    world.spawnEntity(new EntityItem(world, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, item));
                }
            }
        } else if (world.isRemote) {
            // Show the border for the quarry area.
            for (BlockPos dustPos : BlockPos.getAllInBox(new BlockPos(min.getX(), pos.getY(), min.getZ()),
                    new BlockPos(max.getX(), pos.getY(), max.getZ()))) {
                world.spawnParticle(EnumParticleTypes.REDSTONE, true, dustPos.getX() + 0.5, pos.getY(), dustPos.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    /**
     * Check if the state given is valid for mining or if the cursor needs to move.
     *
     * @param state the state to check.
     * @return true if the state can be mined, false otherwise.
     */
    public boolean isStateValid(IBlockState state) {
        return state.getBlock() != Blocks.AIR && state.getBlock() != Blocks.BEDROCK;
    }

    /**
     * Checks if the quarry is currently active with power.
     *
     * @return true if the quarry can run, false otherwise.
     */
    public boolean isActive() {
        return world.getBlockState(pos).getValue(BlockBeamQuarry.ACTIVE);
    }

    /**
     * Set the bounds to mine within, also updates the cursor.
     *
     * @param min the minimum position mining will be restricted in.
     * @param max the maximum position mining will be restricted in.
     */
    private void setBounds(BlockPos min, BlockPos max) {
        this.min = min;
        this.max = max;
        this.cursor = min;

        if (!world.isRemote) {
            TeckleLog.info("Set to {} {}", min, max);
            new TileUpdateMessage(world, pos).sendToAllWatching(this);
        }
    }

    public void setDimensions(int left, int right, int forward) {
        setDimensions(getFacing().getOpposite(), left, right, forward);
    }

    public void setDimensions(EnumFacing facing, int left, int right, int forward) {
        BlockPos basePos = pos.offset(facing);
        EnumFacing relativeLeft = facing.rotateYCCW();
        EnumFacing relativeRight = facing.rotateY();
        BlockPos min = basePos.offset(relativeLeft, left);
        BlockPos max = basePos.offset(relativeRight, right);
        max = max.offset(facing, forward);
        this.setBounds(min, max);
        this.left = left;
        this.right = right;
        this.forward = forward;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        this.readFromNBT(tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.getNbtCompound());
    }

    public boolean isUsableByPlayer(EntityPlayer player) {
        return this.world.getTileEntity(this.pos) == this && player.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public Object getServerElement(EntityPlayer player) {
        return new ContainerBeamQuarry(this, player);
    }

    @Override
    public Object getClientElement(EntityPlayer player) {
        return new GuiBeamQuarry(this, player);
    }

    public EnumFacing getFacing() {
        return world.getBlockState(pos).getValue(BlockBeamQuarry.FACING);
    }
}