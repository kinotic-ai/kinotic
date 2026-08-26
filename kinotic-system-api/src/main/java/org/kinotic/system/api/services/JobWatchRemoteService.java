package org.kinotic.system.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.management.api.services.JobWatchService;

/**
 * Published surface of {@link JobWatchService}, so remote monitoring of grind runs is
 * addressable in the system-api zone while in-process consumers keep injecting the
 * unpublished port.
 */
@Publish
public interface JobWatchRemoteService extends JobWatchService {
}
