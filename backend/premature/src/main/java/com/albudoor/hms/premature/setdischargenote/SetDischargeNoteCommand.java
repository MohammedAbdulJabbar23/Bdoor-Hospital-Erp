package com.albudoor.hms.premature.setdischargenote;

import jakarta.validation.constraints.NotNull;

public record SetDischargeNoteCommand(@NotNull String note) {}
