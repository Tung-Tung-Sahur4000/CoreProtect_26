package net.coreprotect.listener.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockSpreadEvent;

import net.coreprotect.bukkit.BukkitAdapter;
import net.coreprotect.config.Config;
import net.coreprotect.consumer.Queue;
import net.coreprotect.model.BlockGroup;
import net.coreprotect.thread.CacheHandler;
import net.coreprotect.utility.Util;
import net.coreprotect.utility.WorldUtils;

public final class BlockSpreadListener extends Queue implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    protected void onBlockSpread(BlockSpreadEvent event) {
        // mushrooms, fire

        /* To-do: Improve configuration
         *
         * # Track when a block changes states, such as from natural block growth.
         * block-change: true
         *
         */
        if (event.isCancelled()) {
            return;
        }

        BlockState blockstate = event.getNewState();
        Material type = blockstate.getType();
        Config config = Config.getConfig(event.getBlock().getWorld());

        if (config.VINE_GROWTH && (BlockGroup.VINES.contains(type) || BlockGroup.AMETHYST.contains(type) || type == Material.CHORUS_FLOWER || type == Material.BAMBOO)) {
            Block block = event.getBlock();
            if (config.DUPLICATE_SUPPRESSION && checkCacheData(block, type)) {
                return;
            }

            if (BlockGroup.VINES.contains(type)) {
                queueBlockPlace("#vine", block.getState(), block.getType(), null, type, -1, 0, blockstate.getBlockData().getAsString());
            }
            else if (BlockGroup.AMETHYST.contains(type)) {
                queueBlockPlace("#amethyst", block.getState(), block.getType(), block.getState(), type, -1, 0, blockstate.getBlockData().getAsString());
            }
            else if (type.equals(Material.CHORUS_FLOWER)) {
                Block sourceBlock = event.getSource();
                Queue.queueBlockPlaceDelayed("#chorus", sourceBlock.getLocation(), sourceBlock.getType(), null, sourceBlock.getState(), 0);
                Queue.queueBlockPlaceDelayed("#chorus", block.getLocation(), block.getType(), null, block.getState(), 0);
            }
            else if (type.equals(Material.BAMBOO)) {
                // Skipped — bamboo growing taller has no anti-grief value; the initial player placement
                // is still logged by BlockPlaceListener and player-break is still logged on harvest.
                return;
            }
        }
        else if (config.SCULK_SPREAD && BlockGroup.SCULK.contains(type)) {
            Block block = event.getBlock();
            if (config.DUPLICATE_SUPPRESSION && checkCacheData(block, type)) {
                return;
            }

            queueBlockPlace("#sculk_catalyst", block.getState(), block.getType(), block.getState(), type, -1, 0, blockstate.getBlockData().getAsString());
        }
    }

    private boolean checkCacheData(Block block, Material type) {
        String cacheId = block.getX() + "." + block.getY() + "." + block.getZ() + "." + WorldUtils.getWorldId(block.getWorld().getName());
        Location location = block.getLocation();
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        Object[] cacheData = CacheHandler.spreadCache.get(cacheId);
        CacheHandler.spreadCache.put(cacheId, new Object[] { timestamp, type });
        if (cacheData != null && ((Material) cacheData[1]) == type) {
            return true;
        }

        return false;
    }
}
