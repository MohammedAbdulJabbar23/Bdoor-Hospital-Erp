package com.albudoor.hms.doctorappointment.setschedule;

import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.domain.WeeklyHour;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SetScheduleHandler {

    private final DoctorRepository repo;

    public SetScheduleHandler(DoctorRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Doctor handle(UUID doctorId, SetScheduleCommand cmd) {
        Doctor doctor = repo.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found: " + doctorId));
        // WeeklyHour.of validates start<end and 1<=slot<=240; here we add the cross-block
        // rules: a slot must fit inside its block, and blocks on the same day must not overlap.
        List<WeeklyHour> hours = cmd.blocks().stream()
                .map(b -> {
                    WeeklyHour wh = WeeklyHour.of(b.dayOfWeek(), b.startTime(), b.endTime(), b.slotMinutes());
                    long blockMinutes = Duration.between(wh.getStartTime(), wh.getEndTime()).toMinutes();
                    if (wh.getSlotMinutes() > blockMinutes) {
                        throw new DomainException("SLOT_LONGER_THAN_BLOCK",
                                "slotMinutes (" + wh.getSlotMinutes() + ") exceeds the block length ("
                                        + blockMinutes + " min) for " + wh.getDayOfWeek());
                    }
                    return wh;
                })
                .toList();
        rejectOverlaps(hours);
        doctor.replaceSchedule(hours);
        return doctor;
    }

    /** Two blocks on the same day-of-week may not overlap (touching end==start is allowed). */
    private static void rejectOverlaps(List<WeeklyHour> hours) {
        List<WeeklyHour> sorted = new ArrayList<>(hours);
        sorted.sort(Comparator
                .comparing(WeeklyHour::getDayOfWeek)
                .thenComparing(WeeklyHour::getStartTime));
        for (int i = 1; i < sorted.size(); i++) {
            WeeklyHour prev = sorted.get(i - 1);
            WeeklyHour cur = sorted.get(i);
            if (prev.getDayOfWeek() == cur.getDayOfWeek()) {
                LocalTime prevEnd = prev.getEndTime();
                LocalTime curStart = cur.getStartTime();
                if (curStart.isBefore(prevEnd)) {
                    throw new DomainException("SCHEDULE_BLOCKS_OVERLAP",
                            "Overlapping schedule blocks on " + cur.getDayOfWeek()
                                    + ": " + prev.getStartTime() + "-" + prevEnd
                                    + " and " + curStart + "-" + cur.getEndTime());
                }
            }
        }
    }
}
