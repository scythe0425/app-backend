package org.dcode.artificialswbackend.puzzle.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public class DateSegmentUtil {
    public static DateSegmentInfo getDateSegmentInfo() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int day = today.getDayOfMonth();
        int lastDay = YearMonth.now().atEndOfMonth().getDayOfMonth();

        int segmentSize = (int) Math.ceil(lastDay / 5.0);
        int setIndex = (day - 1) / segmentSize;

        int position = (day <= 7 || (15 < day && day <= 22)) ? 3 : 4;
        int period = (day <= 15) ? 1 : 2;

        return new DateSegmentInfo(day, lastDay, setIndex, position, period);
    }
}
