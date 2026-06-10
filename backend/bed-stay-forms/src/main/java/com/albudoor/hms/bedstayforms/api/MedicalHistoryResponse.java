package com.albudoor.hms.bedstayforms.api;

/** GET payload: prefill always present; form null until first save. */
public record MedicalHistoryResponse(StayPrefillDto prefill, MedicalHistoryDto form) {}
