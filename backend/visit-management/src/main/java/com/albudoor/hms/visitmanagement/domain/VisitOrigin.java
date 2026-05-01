package com.albudoor.hms.visitmanagement.domain;

/**
 * How the visit entered the system.
 *
 * <ul>
 *   <li><b>DIRECT_NEW</b> — first-time patient walking in; reception completes GDF.
 *   <li><b>DIRECT_RETURNING</b> — returning patient; existing record loaded.
 *   <li><b>FORWARDED</b> — sub-visit created when another visit was forwarded
 *       to a department; carries a {@code parentVisitId}.
 * </ul>
 */
public enum VisitOrigin {
    DIRECT_NEW,
    DIRECT_RETURNING,
    FORWARDED
}
