package com.hopeful117.devlogai.retrieval.job;

import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import com.hopeful117.devlogai.retrieval.repository.RetrievalIndexingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetrievalIndexingQueueProcessor {

    private final RetrievalIndexingQueueRepository queueRepository;
    private final RetrievalIndexingQueueProcessorDelegate delegate;

    @Scheduled(fixedDelayString = "${devlog.retrieval.queue.process-delay-ms:30000}")
    public void process() {
        List<RetrievalIndexingQueueEntry> pending =
                queueRepository.findTop10ByStatusOrderByQueuedAtAsc("PENDING");
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Processing {} pending retrieval indexing queue entries", pending.size());
        for (RetrievalIndexingQueueEntry entry : pending) {
            delegate.processEntry(entry);
        }
    }
}
