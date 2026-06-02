package com.albudoor.hms.emergency.setdischargenote;

import jakarta.validation.constraints.NotNull;

public record SetDischargeNoteCommand(@NotNull String note) {}
