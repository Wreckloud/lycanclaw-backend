package com.lycanclaw.backend.music.service;

import com.lycanclaw.backend.music.dto.MusicQueueItemDto;
import com.lycanclaw.backend.music.dto.MusicQueueSnapshotDto;
import com.lycanclaw.backend.music.dto.QueueEnqueueRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class MusicQueueService {

    private final MusicDataService musicDataService;
    private final Deque<MusicQueueItemDto> queue = new ArrayDeque<>();
    private MusicQueueItemDto current;

    public MusicQueueService(MusicDataService musicDataService) {
        this.musicDataService = musicDataService;
    }

    public synchronized Map<String, Object> enqueue(QueueEnqueueRequest request) {
        if (request == null || request.id() == null || request.id().isBlank()) {
            throw new IllegalArgumentException("入队失败：歌曲 id 不能为空");
        }

        String source = request.source() == null || request.source().isBlank() ? "unknown" : request.source().trim();
        int priority = request.priority() == null ? 1 : Math.max(1, request.priority());
        boolean insertFront = Boolean.TRUE.equals(request.insertFront());
        boolean interruptCurrent = Boolean.TRUE.equals(request.interruptCurrent());
        boolean resumeCurrent = request.resumeCurrent() == null || request.resumeCurrent();
        String dedupeMode = normalizeDedupeMode(request.dedupeMode());

        Map<String, Object> detail = musicDataService.getTrackDetailWithUrl(request.id().trim(), request.level());
        MusicQueueItemDto item = buildItem(detail, source, priority);

        if ("skip".equals(dedupeMode)) {
            if (current != null && Objects.equals(current.id(), item.id())) {
                return Map.of(
                        "action", "已在播放中，跳过",
                        "item", current,
                        "snapshot", snapshot(30)
                );
            }
            MusicQueueItemDto duplicated = findInQueueBySongId(item.id());
            if (duplicated != null) {
                return Map.of(
                        "action", "队列中已有同曲目，跳过",
                        "item", duplicated,
                        "snapshot", snapshot(30)
                );
            }
        }

        if (!"allow".equals(dedupeMode)) {
            queue.removeIf(existing -> Objects.equals(existing.id(), item.id()));
        }

        if (current == null) {
            current = item;
            return Map.of(
                    "action", "设为当前播放",
                    "item", item,
                    "snapshot", snapshot(30)
            );
        }

        if (Objects.equals(current.id(), item.id())) {
            return Map.of(
                    "action", "已是当前播放",
                    "item", current,
                    "snapshot", snapshot(30)
            );
        }

        if (interruptCurrent) {
            MusicQueueItemDto interrupted = current;
            current = item;
            if (resumeCurrent) {
                queue.addFirst(interrupted);
            }
            return Map.of(
                    "action", "打断当前播放并插队",
                    "item", item,
                    "snapshot", snapshot(30)
            );
        }

        if (insertFront) {
            queue.addFirst(item);
        } else {
            queue.addLast(item);
        }

        return Map.of(
                "action", "加入队列",
                "item", item,
                "snapshot", snapshot(30)
        );
    }

    public synchronized Map<String, Object> setCurrentByQueueId(String queueId, boolean resumeCurrent) {
        if (queueId == null || queueId.isBlank()) {
            throw new IllegalArgumentException("切换失败：queueId 不能为空");
        }

        MusicQueueItemDto target = removeByQueueIdInternal(queueId.trim());
        if (target == null) {
            throw new IllegalArgumentException("切换失败：队列中未找到对应 queueId");
        }

        MusicQueueItemDto previous = current;
        current = target;
        if (resumeCurrent && previous != null && !Objects.equals(previous.queueId(), target.queueId())) {
            queue.addFirst(previous);
        }

        return Map.of(
                "action", "已切换当前播放",
                "current", current,
                "previous", previous,
                "snapshot", snapshot(30)
        );
    }

    public synchronized Map<String, Object> removeByQueueId(String queueId) {
        if (queueId == null || queueId.isBlank()) {
            throw new IllegalArgumentException("移除失败：queueId 不能为空");
        }
        MusicQueueItemDto removed = removeByQueueIdInternal(queueId.trim());
        return Map.of(
                "removed", removed,
                "snapshot", snapshot(30)
        );
    }

    public synchronized Map<String, Object> playNext() {
        MusicQueueItemDto previous = current;
        current = queue.pollFirst();
        return Map.of(
                "previous", previous,
                "current", current,
                "snapshot", snapshot(30)
        );
    }

    public synchronized Map<String, Object> clear(boolean keepCurrent) {
        queue.clear();
        if (!keepCurrent) {
            current = null;
        }
        return Map.of(
                "keepCurrent", keepCurrent,
                "snapshot", snapshot(30)
        );
    }

    public synchronized MusicQueueSnapshotDto snapshot(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<MusicQueueItemDto> items = new ArrayList<>(safeLimit);
        int count = 0;
        for (MusicQueueItemDto item : queue) {
            items.add(item);
            count++;
            if (count >= safeLimit) {
                break;
            }
        }
        return new MusicQueueSnapshotDto(current, queue.size(), items);
    }

    private MusicQueueItemDto buildItem(Map<String, Object> detail, String source, int priority) {
        String id = asText(detail.get("id"));
        String name = asText(detail.get("name"));
        String artist = asText(detail.get("artist"));
        String cover = asText(detail.get("cover"));
        String url = asText(detail.get("url"));

        if (id.isBlank() || name.isBlank()) {
            throw new IllegalStateException("歌曲详情不完整，无法入队");
        }

        return new MusicQueueItemDto(
                UUID.randomUUID().toString(),
                id,
                name,
                artist,
                cover,
                url,
                source,
                priority,
                Instant.now().toString()
        );
    }

    private String asText(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    private String normalizeDedupeMode(String dedupeMode) {
        if (dedupeMode == null || dedupeMode.isBlank()) {
            return "replace";
        }
        String mode = dedupeMode.trim().toLowerCase();
        return switch (mode) {
            case "replace", "skip", "allow" -> mode;
            default -> "replace";
        };
    }

    private MusicQueueItemDto findInQueueBySongId(String songId) {
        for (MusicQueueItemDto item : queue) {
            if (Objects.equals(item.id(), songId)) {
                return item;
            }
        }
        return null;
    }

    private MusicQueueItemDto removeByQueueIdInternal(String queueId) {
        Iterator<MusicQueueItemDto> iterator = queue.iterator();
        while (iterator.hasNext()) {
            MusicQueueItemDto item = iterator.next();
            if (Objects.equals(item.queueId(), queueId)) {
                iterator.remove();
                return item;
            }
        }
        return null;
    }
}
