package com.lycanclaw.backend.music.service;

import com.lycanclaw.backend.music.dto.MusicQueueItemDto;
import com.lycanclaw.backend.music.dto.MusicQueueSnapshotDto;
import com.lycanclaw.backend.music.dto.QueueEnqueueRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 音乐队列服务。
 * 管理当前播放和等待队列，只向前播放。
 * @author Wreckloud
 * @since 2026-05-15
 */
@Service
public class MusicQueueService {

    private static final String ACTION_SET_CURRENT = "设为当前播放";
    private static final String ACTION_ALREADY_CURRENT = "已是当前播放";
    private static final String ACTION_ENQUEUED = "加入队列";
    private static final String ACTION_NEXT = "已切到下一首";

    private final MusicDataService musicDataService;
    private final Deque<MusicQueueItemDto> queue = new ArrayDeque<>();
    private MusicQueueItemDto current;

    public MusicQueueService(MusicDataService musicDataService) {
        this.musicDataService = musicDataService;
    }

    // 入队核心逻辑：固定追加到队尾，不支持插队/打断，保持“只向前播放”。
    /**
     * 添加歌曲到播放队列。
     */
    public Map<String, Object> enqueue(QueueEnqueueRequest request) {
        if (request == null || request.id() == null || request.id().isBlank()) {
            throw new IllegalArgumentException("入队失败：歌曲 id 不能为空");
        }

        String source = request.source() == null || request.source().isBlank() ? "unknown" : request.source().trim();
        Map<String, Object> detail = musicDataService.getTrackDetailWithUrl(request.id().trim(), request.level());
        MusicQueueItemDto item = buildItem(detail, source, 1);
        return enqueueResolvedItem(item);
    }

    // 只在内存状态变更阶段加锁，避免网络 IO 占用队列锁。
    private synchronized Map<String, Object> enqueueResolvedItem(MusicQueueItemDto item) {
        if (current == null) {
            current = item;
            return response(ACTION_SET_CURRENT, item);
        }

        if (Objects.equals(current.id(), item.id())) {
            return response(ACTION_ALREADY_CURRENT, current);
        }

        // 同曲目替换旧等待项，避免队列无限重复堆积。
        queue.removeIf(existing -> Objects.equals(existing.id(), item.id()));
        queue.addLast(item);
        return response(ACTION_ENQUEUED, item);
    }

    // 切到下一首：当前项丢弃，队首补位为新 current。
    /**
     * 切换到下一首歌曲。
     */
    public synchronized Map<String, Object> playNext() {
        current = queue.pollFirst();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", ACTION_NEXT);
        data.put("current", current);
        data.put("snapshot", snapshot(3));
        return data;
    }

    // 清空等待队列；可配置是否保留当前播放项。
    /**
     * 清空等待队列，可选保留当前播放项。
     */
    public synchronized Map<String, Object> clear(boolean keepCurrent) {
        queue.clear();
        if (!keepCurrent) {
            current = null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keepCurrent", keepCurrent);
        data.put("snapshot", snapshot(3));
        return data;
    }

    // 给前端返回当前快照（current + 队列长度 + 最多 3 首预览）。
    /**
     * 获取当前播放快照（当前项 + 队列长度 + 预览）。
     */
    public synchronized MusicQueueSnapshotDto snapshot(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 3));
        List<MusicQueueItemDto> preview = new ArrayList<>(safeLimit);
        int count = 0;
        for (MusicQueueItemDto item : queue) {
            preview.add(item);
            count++;
            if (count >= safeLimit) {
                break;
            }
        }
        return new MusicQueueSnapshotDto(current, queue.size(), preview);
    }

    private MusicQueueItemDto buildItem(Map<String, Object> detail, String source, int priority) {
        String id = asText(detail.get("id"));
        String name = asText(detail.get("name"));
        String artist = asText(detail.get("artist"));
        String cover = asText(detail.get("cover"));
        String url = asText(detail.get("url"));
        String urlSource = asText(detail.get("source"));

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
                urlSource,
                priority,
                Instant.now().toString()
        );
    }

    private String asText(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    private Map<String, Object> response(String action, MusicQueueItemDto item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("item", item);
        data.put("snapshot", snapshot(3));
        return data;
    }
}
