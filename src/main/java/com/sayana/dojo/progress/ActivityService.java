package com.sayana.dojo.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学習した日の記録と、連続学習日数の計算。 */
@Service
@Transactional
public class ActivityService {

    private final StudyDayRepository days;
    private final Clock clock;

    public ActivityService(StudyDayRepository days, Clock clock) {
        this.days = days;
        this.clock = clock;
    }

    public void record() {
        LocalDate today = LocalDate.now(clock);
        StudyDay day = days.findById(today).orElseGet(() -> days.save(new StudyDay(today)));
        day.increment();
    }

    /** 今日(まだ今日やっていなければ昨日)から遡って何日連続で学習しているか。 */
    @Transactional(readOnly = true)
    public int currentStreak() {
        Set<LocalDate> set = days.findAll().stream().map(StudyDay::getDay).collect(Collectors.toSet());
        LocalDate d = LocalDate.now(clock);
        if (!set.contains(d)) {
            d = d.minusDays(1);
        }
        int streak = 0;
        while (set.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    @Transactional(readOnly = true)
    public boolean studiedToday() {
        return days.existsById(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public long totalDays() {
        return days.count();
    }

    /** 直近 n 日分の学習量(古い順)。 */
    @Transactional(readOnly = true)
    public List<DayCell> recentDays(int n) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(n - 1L);
        var map = days.findByDayGreaterThanEqualOrderByDayAsc(from).stream()
                .collect(Collectors.toMap(StudyDay::getDay, StudyDay::getActions));
        return from.datesUntil(today.plusDays(1)).map(d -> new DayCell(d, map.getOrDefault(d, 0))).toList();
    }

    public record DayCell(LocalDate day, int actions) {

        public int level() {
            return actions == 0 ? 0 : actions < 3 ? 1 : actions < 8 ? 2 : 3;
        }
    }
}
