package kz.uniplan.app.data.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.DatasetImportEntity;
import kz.uniplan.app.data.local.entity.LessonEntity;
import kz.uniplan.app.data.local.entity.ProfileEntity;
import kz.uniplan.app.data.local.entity.SubjectEntity;

/** The exact personal timetable supplied by the user. No network requests are made. */
public final class PersonalSchedule {
    public static final String OWNER_UID = "demo-1";
    public static final String DATASET_KEY = "ibrahim_azhq321_timetable_v1";
    private PersonalSchedule() {}

    /** Called on the repository worker. Preserves study records; sets the requested profile once. */
    public static boolean importOnce(AppDatabase db, String ownerUid) {
        if (!OWNER_UID.equals(ownerUid)) return false;
        return db.runInTransaction(() -> {
            StudyDao dao = db.studyDao();
            if (dao.datasetImported(ownerUid, DATASET_KEY) != 0) return false;
            Map<String, Long> existingSubjects = new HashMap<>();
            for (SubjectEntity value : dao.subjects(ownerUid)) existingSubjects.putIfAbsent(value.name, value.id);
            long[] subjectIds = new long[SUBJECTS.length];
            for (int i = 0; i < SUBJECTS.length; i++) {
                SubjectSpec spec = SUBJECTS[i];
                Long existing = existingSubjects.get(spec.name);
                if (existing != null) subjectIds[i] = existing;
                else {
                    SubjectEntity subject = new SubjectEntity();
                    subject.ownerUid = ownerUid; subject.name = spec.name; subject.teacher = spec.teacher;
                    subject.color = spec.color; subject.icon = spec.icon;
                    subjectIds[i] = dao.insertSubject(subject);
                }
            }
            List<LessonEntity> existingLessons = dao.lessons(ownerUid);
            for (Slot slot : SLOTS) {
                LessonEntity lesson = new LessonEntity();
                lesson.ownerUid = ownerUid; lesson.subjectId = subjectIds[slot.subject];
                lesson.dayOfWeek = slot.day; lesson.lessonNumber = slot.number;
                lesson.startMinute = slot.start; lesson.endMinute = slot.end;
                lesson.lessonType = slot.type; lesson.teacher = slot.teacher;
                lesson.room = slot.room; lesson.meetingUrl = slot.url;
                boolean duplicate = false;
                for (LessonEntity existing : existingLessons) if (sameLesson(existing, lesson)) { duplicate = true; break; }
                if (!duplicate) dao.insertLesson(lesson);
            }
            ProfileEntity profile = new ProfileEntity();
            profile.ownerUid = ownerUid; profile.displayName = "Ибраһим"; profile.studyGroup = "АЖҚ-321";
            dao.insertProfile(profile);
            dao.updateProfile(ownerUid, profile.displayName, profile.studyGroup);
            DatasetImportEntity marker = new DatasetImportEntity();
            marker.ownerUid = ownerUid; marker.datasetKey = DATASET_KEY; marker.importedAt = System.currentTimeMillis();
            dao.insertDatasetImport(marker);
            return true;
        });
    }
    private static boolean sameLesson(LessonEntity a, LessonEntity b) {
        return a.subjectId == b.subjectId && a.dayOfWeek == b.dayOfWeek && a.lessonNumber == b.lessonNumber
                && a.startMinute == b.startMinute && a.endMinute == b.endMinute && a.lessonType.equals(b.lessonType)
                && a.teacher.equals(b.teacher) && a.room.equals(b.room) && a.meetingUrl.equals(b.meetingUrl);
    }
    private static final class SubjectSpec {
        final String name, teacher; final int color, icon;
        SubjectSpec(String name, String teacher, int color, int icon) {
            this.name = name; this.teacher = teacher; this.color = color; this.icon = icon;
        }
    }
    private static final class Slot {
        final int day, number, start, end, subject;
        final String type, teacher, room, url;
        Slot(int day, int number, int start, int end, int subject, String type, String teacher, String room, String url) {
            this.day = day; this.number = number; this.start = start; this.end = end; this.subject = subject;
            this.type = type; this.teacher = teacher; this.room = room; this.url = url;
        }
    }
    private static final SubjectSpec[] SUBJECTS = {
        new SubjectSpec("3D Max-та моделдеу", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", 0xff576cbb, 0),
        new SubjectSpec("1С тілінде бағдарламалау", "педагогика ғылымдарының кандидаты\nкафедра меңгерушісі\nСмагулова Лаура Амангельдиевна", 0xff428b65, 1),
        new SubjectSpec("JavaScript-те программалау", "ph. d.\nмагистр\nоқытушы - ассистент\nСатқұлов Бақтияр Бағланұлы", 0xffc08b32, 2),
        new SubjectSpec("Видео және аудио ақпаратты сандық өңдеу", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", 0xffa85f8b, 0),
        new SubjectSpec("Инженерлік және компьютерлік графика", "магистр\nоқытушы - дәріскер\nШалтабаев Алтай Аканович", 0xff5f8b9a, 1),
        new SubjectSpec("Ақпараттық технологиядағы нейрондық желілер", "ph. d.\nоқытушы - дәріскер\nАбдуалиева Рима Ергалиевна", 0xff8068a8, 2),
        new SubjectSpec("Java тілінде мобильді қосымшаларды құру", "магистр\nоқытушы - дәріскер\nМейрамбек Әділет Мейрамбекұлы", 0xffb96c4b, 0)
    };
    private static final Slot[] SLOTS = {
        new Slot(1, 1, 510, 560, 0, "Дәріс", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", "314 (№ 2 оқу ғимараты)", "http://meet.google.com/mhi-wnyv-ind"),
        new Slot(1, 2, 565, 615, 0, "Дәріс", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", "314 (№ 2 оқу ғимараты)", "http://meet.google.com/mhi-wnyv-ind"),
        new Slot(1, 3, 630, 680, 1, "Дәріс", "педагогика ғылымдарының кандидаты\nкафедра меңгерушісі\nСмагулова Лаура Амангельдиевна", "321 (А.Т.Қожамқұлов атындағы сымсыз компьютерлік желілер зертханасы) (№ 2 оқу ғимараты)", "https://meet.google.com/hzr-epvd-kgv"),
        new Slot(1, 4, 685, 735, 2, "Зертханалық сабақ", "магистр\nоқытушы - ассистент\nСерікмұрат Аружан Асхатқызы", "317 (№ 2 оқу ғимараты)", ""),
        new Slot(1, 5, 740, 790, 2, "Зертханалық сабақ", "магистр\nоқытушы - ассистент\nСерікмұрат Аружан Асхатқызы", "317 (№ 2 оқу ғимараты)", ""),
        new Slot(1, 10, 1025, 1075, 3, "Дәріс", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", "321 (А.Т.Қожамқұлов атындағы сымсыз компьютерлік желілер зертханасы) (№ 2 оқу ғимараты)", "http://meet.google.com/mhi-wnyv-ind"),
        new Slot(2, 1, 510, 560, 0, "Практикалық сабақ", "магистр\nоқытушы - дәріскер\nОразбаева Асель Айтмухамедовна", "314 (№ 2 оқу ғимараты)", "http://meet.google.com/mhi-wnyv-ind"),
        new Slot(2, 3, 630, 680, 3, "Зертханалық сабақ", "оқытушы - ассистент\nКусаинов Ғани Талғатұлы", "307 (№ 2 оқу ғимараты)", ""),
        new Slot(2, 4, 685, 735, 3, "Зертханалық сабақ", "оқытушы - ассистент\nКусаинов Ғани Талғатұлы", "307 (№ 2 оқу ғимараты)", ""),
        new Slot(2, 7, 850, 900, 4, "Практикалық сабақ", "магистр\nоқытушы - ассистент\nСагындыкова Шынар Моряковна", "322 (№ 2 оқу ғимараты)", "https://meet.google.com/gff-ipdu-fjt"),
        new Slot(3, 4, 685, 735, 5, "Дәріс", "ph. d.\nоқытушы - дәріскер\nАбдуалиева Рима Ергалиевна", "314 (№ 2 оқу ғимараты)", "https://meet.google.com/ept-jrrf-skx"),
        new Slot(3, 8, 915, 965, 5, "Зертханалық сабақ", "ph. d.\nоқытушы - дәріскер\nАбдуалиева Рима Ергалиевна", "№2 оқу залы (№ 2 оқу ғимараты)", "https://meet.google.com/ept-jrrf-skx"),
        new Slot(3, 9, 970, 1020, 5, "Зертханалық сабақ", "ph. d.\nоқытушы - дәріскер\nАбдуалиева Рима Ергалиевна", "№2 оқу залы (№ 2 оқу ғимараты)", "https://meet.google.com/ept-jrrf-skx"),
        new Slot(4, 1, 510, 560, 4, "Дәріс", "магистр\nоқытушы - дәріскер\nШалтабаев Алтай Аканович", "323 (Электроника және радиоэлектроника зертханасы) (№ 2 оқу ғимараты)", "https://meet.google.com/fdm-dokz-dhz"),
        new Slot(4, 2, 565, 615, 4, "Дәріс", "магистр\nоқытушы - дәріскер\nШалтабаев Алтай Аканович", "323 (Электроника және радиоэлектроника зертханасы) (№ 2 оқу ғимараты)", "https://meet.google.com/fdm-dokz-dhz"),
        new Slot(4, 3, 630, 680, 6, "Зертханалық сабақ", "оқытушы - ассистент\nКусаинов Ғани Талғатұлы", "Военная кафедра (№ 2 оқу ғимараты)", ""),
        new Slot(4, 4, 685, 735, 1, "Зертханалық сабақ", "педагогика ғылымдарының кандидаты\nкафедра меңгерушісі\nСмагулова Лаура Амангельдиевна", "321 (А.Т.Қожамқұлов атындағы сымсыз компьютерлік желілер зертханасы) (№ 2 оқу ғимараты)", "https://meet.google.com/hzr-epvd-kgv"),
        new Slot(4, 5, 740, 790, 1, "Зертханалық сабақ", "педагогика ғылымдарының кандидаты\nкафедра меңгерушісі\nСмагулова Лаура Амангельдиевна", "321 (А.Т.Қожамқұлов атындағы сымсыз компьютерлік желілер зертханасы) (№ 2 оқу ғимараты)", "https://meet.google.com/hzr-epvd-kgv"),
        new Slot(4, 7, 850, 900, 6, "Дәріс", "магистр\nоқытушы - дәріскер\nМейрамбек Әділет Мейрамбекұлы", "Кабинет Хуавей (№ 1 оқу ғимараты)", "https://meet.google.com/wsf-fqwk-hzm"),
        new Slot(5, 2, 565, 615, 6, "Зертханалық сабақ", "оқытушы - ассистент\nКусаинов Ғани Талғатұлы", "323 (Электроника және радиоэлектроника зертханасы) (№ 2 оқу ғимараты)", ""),
        new Slot(5, 3, 630, 680, 6, "Зертханалық сабақ", "оқытушы - ассистент\nКусаинов Ғани Талғатұлы", "323 (Электроника және радиоэлектроника зертханасы) (№ 2 оқу ғимараты)", ""),
        new Slot(5, 4, 685, 735, 2, "Зертханалық сабақ", "магистр\nоқытушы - ассистент\nСерікмұрат Аружан Асхатқызы", "320 (№ 2 оқу ғимараты)", ""),
        new Slot(6, 4, 685, 735, 2, "Дәріс", "ph. d.\nмагистр\nоқытушы - ассистент\nСатқұлов Бақтияр Бағланұлы", "Талдыкорган қ, Жансугуров көшксі 226", "https://student.zhetysu.edu.kz:8059/%D0%B3.%D0%A2%D0%B0%D0%BB%D0%B4%D1%8B%D0%BA%D0%BE%D1%80%D0%B3%D0%B0%D0%BD%20%D1%83%D0%BB.%20%D0%96%D0%B0%D0%BD%D1%81%D1%83%D0%B3%D1%83%D1%80%D0%BE%D0%B2%D0%B0%20226")
    };
}
