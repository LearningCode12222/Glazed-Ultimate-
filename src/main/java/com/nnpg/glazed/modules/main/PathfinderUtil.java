package com.nnpg.glazed.modules.main;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class PathfinderUtil {
    private final World world;

    public PathfinderUtil(World world) {
        this.world = world;
    }

    // Example pathfinding method (dummy BFS)
    public List<BlockPos> findPath(BlockPos start, BlockPos target, int maxNodes) {
        Queue<BlockPos> open = new LinkedList<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        open.add(start);
        visited.add(start);

        while (!open.isEmpty() && visited.size() < maxNodes) {
            BlockPos current = open.poll();
            if (current.equals(target)) {
                return reconstructPath(cameFrom, current);
            }

            for (BlockPos neighbor : getNeighbors(current)) {
                if (visited.contains(neighbor)) continue;

                BlockState state = world.getBlockState(neighbor);
                // ✅ Updated for modern versions
                if (!state.isAir() && !state.isReplaceable()) continue;

                visited.add(neighbor);
                cameFrom.put(neighbor, current);
                open.add(neighbor);
            }
        }
        return Collections.emptyList();
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        return Arrays.asList(
            pos.north(),
            pos.south(),
            pos.east(),
            pos.west(),
            pos.up(),
            pos.down()
        );
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        while (current != null) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }
}
