package com.hotel.delivery.service;

import com.hotel.delivery.entity.RetryQueueItem;
import com.hotel.delivery.enums.PlatformType;
import com.hotel.delivery.enums.RetryStatus;
import com.hotel.delivery.repository.RetryQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private static final int[] BACKOFF_SECONDS = {60, 300, 900};

    private final RetryQueueRepository retryQueueRepository;

    @Transactional
    public void enqueue(PlatformType platformType, String operationType,
                        String platformOrderId, String payload) {
        RetryQueueItem item = RetryQueueItem.builder()
                .platformType(platformType)
                .operationType(operationType)
                .platformOrderId(platformOrderId)
                .payload(payload)
                .status(RetryStatus.PENDING)
                .nextRetryAt(LocalDateTime.now().plusSeconds(60))
                .build();
        retryQueueRepository.save(item);
        log.info("Enqueued retry: {} for order {}", operationType, platformOrderId);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processRetries() {
        List<RetryQueueItem> due = retryQueueRepository
                .findByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        RetryStatus.PENDING, LocalDateTime.now());

        for (RetryQueueItem item : due) {
            try {
                item.setStatus(RetryStatus.IN_PROGRESS);
                retryQueueRepository.save(item);

                performRetry(item);

                item.setStatus(RetryStatus.SUCCEEDED);
                retryQueueRepository.save(item);
                log.info("Retry succeeded for {} order {}", item.getOperationType(), item.getPlatformOrderId());

            } catch (Exception e) {
                item.setAttemptCount(item.getAttemptCount() + 1);
                item.setLastError(e.getMessage());

                if (item.getAttemptCount() >= item.getMaxAttempts()) {
                    item.setStatus(RetryStatus.EXHAUSTED);
                    log.error("Retry exhausted for {} order {}: {}",
                            item.getOperationType(), item.getPlatformOrderId(), e.getMessage());
                } else {
                    int delaySec = BACKOFF_SECONDS[Math.min(item.getAttemptCount() - 1, BACKOFF_SECONDS.length - 1)];
                    item.setStatus(RetryStatus.PENDING);
                    item.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySec));
                    log.warn("Retry #{} failed for {} — next in {}s",
                            item.getAttemptCount(), item.getOperationType(), delaySec);
                }
                retryQueueRepository.save(item);
            }
        }
    }

    private void performRetry(RetryQueueItem item) {
        // Retry dispatch: parse operationType and call appropriate service method.
        // This is intentionally simple — extend with a command pattern if operations grow.
        log.info("[RETRY] Executing {} for platform {} order {}",
                item.getOperationType(), item.getPlatformType(), item.getPlatformOrderId());
        // TODO: inject DeliveryIntegrationService and dispatch based on operationType
    }
}
