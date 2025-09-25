package com.nnpg.glazed.utils.glazed;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class PathfinderUtil {
    public static List<BlockPos> findPath(World world, BlockPos start, BlockPos target, int maxRange) {
        if (world == null || start == null || target == null) return Collections.emptyList();

        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            if (current.equals(target)) {
                return reconstructPath(cameFrom, current);
            }

            for (BlockPos neighbor : getNeighbors(current)) {
                if (visited.contains(neighbor)) continue;
                if (start.getManhattanDistance(neighbor) > maxRange) continue;

                BlockState state = world.getBlockState(neighbor);
                if (isHazard(state)) continue; // skip hazards

                if (!state.isAir() && !state.getMaterial().isReplaceable()) continue;

                visited.add(neighbor);
                cameFrom.put(neighbor, current);
                queue.add(neighbor);
            }
        }
        return Collections.emptyList();
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        while (cameFrom.containsKey(current)) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    private static List<BlockPos> getNeighbors(BlockPos pos) {
        return Arrays.asList(
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.up(),
            pos.down()
        );
    }

    private static boolean isHazard(BlockState state) {
        if (state == null) return false;
        return state.getBlock() == Blocks.LAVA ||
               state.getBlock() == Blocks.WATER ||
               state.getBlock() == Blocks.GRAVEL ||
               state.getBlock() == Blocks.SAND ||
               state.getBlock() == Blocks.POWDER_SNOW;
    }
}
