package com.albudoor.hms.bedstayforms.directory;

import java.time.Instant;
import java.util.UUID;

/** A forwarded order placed from a stay: the child visit + where it went. */
public record StayOrderRef(UUID visitId, String targetType, Instant orderedAt) {}
