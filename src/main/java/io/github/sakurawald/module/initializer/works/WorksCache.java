package io.github.sakurawald.module.initializer.works;

import io.github.sakurawald.module.initializer.works.work_type.Work;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class WorksCache {
    @Getter
    private static final ConcurrentHashMap<BlockPos, HashSet<Work>> blockpos2works = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Integer, HashSet<Work>> entity2works = new ConcurrentHashMap<>();

    public static void bind(BlockPos blockPos, Work work) {
        blockpos2works.computeIfAbsent(blockPos, k -> new HashSet<>()).add(work);
    }

    public static void bind(Integer entityID, Work work) {
        entity2works.computeIfAbsent(entityID, k -> new HashSet<>()).add(work);
    }

    public static void unbind(Work work) {
        Iterator<Map.Entry<BlockPos, HashSet<Work>>> iter1 = blockpos2works.entrySet().iterator();
        while (iter1.hasNext()) {
            Map.Entry<BlockPos, HashSet<Work>> entry = iter1.next();
            entry.getValue().remove(work);
            if (entry.getValue().isEmpty()) {
                iter1.remove();
            }
        }
        Iterator<Map.Entry<Integer, HashSet<Work>>> iter2 = entity2works.entrySet().iterator();
        while (iter2.hasNext()) {
            Map.Entry<Integer, HashSet<Work>> entry = iter2.next();
            entry.getValue().remove(work);
            if (entry.getValue().isEmpty()) {
                iter2.remove();
            }
        }
    }
}
