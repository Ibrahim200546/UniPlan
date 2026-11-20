package kz.uniplan.app.domain;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.Test;
import kz.uniplan.app.domain.schedule.*;

public class ScheduleParserTest {
    @Test public void syntheticTimetablePreservesAll23LessonsAndMetadata() throws Exception {
        String input=new String(getClass().getResourceAsStream("/synthetic_schedule.md").readAllBytes(),StandardCharsets.UTF_8);
        ScheduleImport.Preview p=ScheduleParser.parse(input);
        assertTrue(p.issues.toString(),p.canImport());assertEquals(23,p.entries.size());
        assertEquals("Сынақ пәні A",p.entries.get(0).subject);assertEquals(520,p.entries.get(0).start);
        assertEquals(565,p.entries.get(0).end);assertEquals("Дәріс",p.entries.get(0).type);
        assertEquals(10,p.entries.get(5).number);assertEquals(1030,p.entries.get(5).start);
        assertEquals("Сынақ профилі\nОқу үлгісі\nТест оқытушысы 01",p.entries.get(0).teacher);
        assertEquals("DEMO-101",p.entries.get(0).room);
        assertEquals("https://example.org/lesson/1",p.entries.get(0).url);
        assertEquals("",p.entries.get(1).url);assertEquals("",p.entries.get(4).room);
        assertEquals(6,p.entries.get(22).day);assertEquals("https://example.net/remote/23",p.entries.get(22).url);
        assertEquals(0,p.ignoredAccessFields);
        int[] counts=new int[7];for(ScheduleImport.Entry row:p.entries)counts[row.day-1]++;
        assertArrayEquals(new int[]{6,4,3,6,3,1,0},counts);
    }
    @Test public void spreadsheetQuotesAndKazakhColumnsAreSupported(){
        ScheduleImport.Preview p=ScheduleParser.parse("Күн\tУақыт\tПән\tОқытушы\nДс\t08:30–09:20\tҚазақ тілі\t\"Ұстаз\nӘли\"\n\t09:25-10:15\tҚазақ тілі\tӘли");
        assertTrue(p.canImport());assertEquals(2,p.entries.size());assertEquals("Ұстаз\nӘли",p.entries.get(0).teacher);assertEquals(1,p.entries.get(1).day);
    }
    @Test public void htmlMergedDaysAndLinksWork(){
        String html="<table><tr><th>День</th><th>Время</th><th>Предмет</th><th>Ссылка ВКС</th></tr><tr><td rowspan='2'>ВТ</td><td>08:30-09:20</td><td>Java</td><td><a href='/meet'>Открыть</a></td></tr><tr><td>09:25-10:15</td><td>Java</td><td></td></tr></table>";
        ScheduleImport.Preview p=ScheduleParser.parse(html,"https://university.example/schedule");
        assertTrue(p.canImport());assertEquals(2,p.entries.size());assertEquals(2,p.entries.get(1).day);assertEquals("https://university.example/meet",p.entries.get(0).url);
    }
    @Test public void errorsBlockPartialImportAndCredentialsAreNotImported(){
        ScheduleImport.Preview p=ScheduleParser.parse("День;Время;Предмет;Логин/пароль ВКС\nПН;08:30-09:20;Java;private\nПН;25:30-26:20;Java;");
        assertFalse(p.canImport());assertEquals(1,p.entries.size());assertEquals("time",p.issues.get(0).code);assertEquals(1,p.ignoredAccessFields);
        assertFalse(ScheduleParser.parse("День;Время;Предмет;Ссылка ВКС\nПН;08:30-09:20;Java;javascript:alert(1)").canImport());
        assertFalse(ScheduleParser.parse("День;Время;Предмет\nUnknown;08:30-09:20;Java").canImport());
    }
    @Test public void nextLessonRollsToNextWeekAndKeepsLocalTimeAcrossDst(){
        ZoneId kz=ZoneId.of("Asia/Qyzylorda");long monday=ZonedDateTime.of(2026,11,23,8,30,0,0,kz).toInstant().toEpochMilli();
        assertEquals(monday,LessonTimes.nextStart(1,510,monday-1,kz));
        assertEquals(monday+7*86400000L,LessonTimes.nextStart(1,510,monday,kz));
        ZoneId zone=ZoneId.of("Europe/Berlin");long before=ZonedDateTime.of(2026,10,19,10,0,0,0,zone).toInstant().toEpochMilli();
        ZonedDateTime next=Instant.ofEpochMilli(LessonTimes.nextStart(1,540,before,zone)).atZone(zone);
        assertEquals(26,next.getDayOfMonth());assertEquals(9,next.getHour());
    }
}
