package kz.uniplan.app.domain.schedule;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import kz.uniplan.app.util.Validation;

/** Immutable, validated preview. No account or database identifiers come from pasted data. */
public final class ScheduleImport {
    public static final int MAX_INPUT = 150000, MAX_ROWS = 500;
    private ScheduleImport() { }
    public static String key(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replace('\u00a0',' ').trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    public static final class Entry {
        public final int sourceRow, day, number, start, end;
        public final String subject, type, teacher, room, url;
        public Entry(int sourceRow,int day,int number,int start,int end,String subject,String type,String teacher,String room,String url) {
            this.sourceRow=sourceRow;this.day=day;this.number=number;this.start=start;this.end=end;
            this.subject=subject;this.type=type;this.teacher=teacher;this.room=room;this.url=url;
        }
        public boolean valid() {
            return Validation.lesson(day,start,end)&&number>=0&&number<=99&&Validation.title(subject)
                    &&type!=null&&type.length()<=100&&teacher!=null&&teacher.length()<=300
                    &&room!=null&&room.length()<=1000&&url!=null&&Validation.webUrl(url);
        }
    }
    public static final class Issue {
        public final int row;
        public final String code;
        public Issue(int row,String code){this.row=row;this.code=code;}
    }
    public static final class Preview {
        public final List<Entry> entries;
        public final List<Issue> issues;
        public final int ignoredAccessFields, ignoredLinkFields;
        public Preview(List<Entry> entries,List<Issue> issues,int ignoredAccessFields,int ignoredLinkFields) {
            this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
            this.issues=Collections.unmodifiableList(new ArrayList<>(issues));this.ignoredAccessFields=ignoredAccessFields;this.ignoredLinkFields=ignoredLinkFields;
        }
        public boolean canImport(){return !entries.isEmpty()&&issues.isEmpty();}
    }
    public static final class Outcome {
        public final int added, duplicates, subjects;
        public Outcome(int added,int duplicates,int subjects){this.added=added;this.duplicates=duplicates;this.subjects=subjects;}
    }
}
