package org.dcode.artificialswbackend.puzzle.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public class DateSegmentInfo {
    private final int day;
    private final int lastDay;
    private final int setIndex;
    private final int position;
    private final int period;

    public DateSegmentInfo(int day, int lastDay, int setIndex, int position, int period) {
        this.day = day;
        this.lastDay = lastDay;
        this.setIndex = setIndex;
        this.position = position;
        this.period = period;
    }

    public int getDay() { return day; }
    public int getLastDay() { return lastDay; }
    public int getSetIndex() { return setIndex; }
    public int getPosition() { return position; }
    public int getPeriod() { return period; }
}
