package kz.uniplan.app.domain.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Weekly wall-clock recurrence follows the device timezone, including DST changes. */
public final class LessonTimes {
    private LessonTimes() { }
    public static long nextStart(int day,int minute,long after,ZoneId zone) {
        ZonedDateTime now=Instant.ofEpochMilli(after).atZone(zone);
        int delta=Math.floorMod(day-now.getDayOfWeek().getValue(),7);
        ZonedDateTime next=now.toLocalDate().plusDays(delta).atTime(minute/60,minute%60).atZone(zone);
        if(next.toInstant().toEpochMilli()<=after)next=next.toLocalDate().plusWeeks(1).atTime(minute/60,minute%60).atZone(zone);
        return next.toInstant().toEpochMilli();
    }
}
