package kz.uniplan.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import android.app.NotificationManager;
import android.content.*;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.*;
import androidx.test.espresso.*;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.hamcrest.Matcher;
import org.junit.*;
import org.junit.runner.RunWith;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.*;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.ui.MainActivity;

@RunWith(AndroidJUnit4.class)
public class ScheduleFeaturesTest {
    private UniPlanApp app;private StudyDao dao;private AuthRepository.Session previous;private Set<Long> originalLessons,originalSubjects;private int originalMinutes;
    @Before public void setup(){
        app=ApplicationProvider.getApplicationContext();assumeTrue(app.auth().isDemo());dao=AppDatabase.get(app).studyDao();
        main(()->{previous=app.auth().current();app.auth().openDemo(2);originalMinutes=app.reminders().minutes();app.reminders().setMinutes(0);});
        originalLessons=new HashSet<>();for(LessonEntity e:dao.lessons("demo-2"))originalLessons.add(e.id);
        originalSubjects=new HashSet<>();for(SubjectEntity e:dao.subjects("demo-2"))originalSubjects.add(e.id);
    }
    @After public void cleanup(){if(dao==null)return;
        for(LessonEntity e:dao.lessons("demo-2"))if(!originalLessons.contains(e.id))dao.deleteLesson("demo-2",e.id);
        for(SubjectEntity e:dao.subjects("demo-2"))if(!originalSubjects.contains(e.id))dao.deleteSubject("demo-2",e.id);
        main(()->{app.reminders().setMinutes(originalMinutes);if(previous==null)app.auth().signOut();else app.auth().openDemo("demo-1".equals(previous.uid)?1:2);});
    }
    @Test public void clipboardPreviewSaveRepeatAndNotification() throws Exception {
        String table=new String(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("synthetic_schedule.md").readAllBytes(),StandardCharsets.UTF_8);
        // Isolated names allow testing beside any existing demo timetable.
        String[] lines=table.split("\n");String token="Import-"+System.nanoTime()+" ";
        for(int i=3;i<lines.length;i++){String[] cells=lines[i].split("\\|",-1);cells[5]=token+cells[5].trim();lines[i]=String.join("|",cells);}
        String fixture=String.join("\n",lines);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newHtmlText("Schedule",fixture,"<table><tr><td>Unrelated clipboard markup</td></tr></table>")));
            scenario.onActivity(activity->((com.google.android.material.bottomnavigation.BottomNavigationView)activity.findViewById(R.id.bottomNav)).setSelectedItemId(R.id.scheduleFragment));
            tap(R.id.easyAdd);tap(R.id.pasteButton);waitEnabled(R.id.importButton);
            assertEquals(app.getString(R.string.import_count,23),find(R.id.summary).getText());
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(new File(app.getExternalFilesDir(null),"features-import.png"));
            tap(R.id.importButton);find(R.id.easyAdd);
            assertEquals(originalLessons.size()+23,dao.lessons("demo-2").size());
            tap(R.id.easyAdd);tap(R.id.pasteButton);waitEnabled(R.id.importButton);tap(R.id.importButton);find(R.id.easyAdd);
            assertEquals(originalLessons.size()+23,dao.lessons("demo-2").size());
            SubjectEntity subject=new SubjectEntity();subject.name="UniPlan notification check";subject.teacher="";
            long subjectId=call(cb->app.data().saveSubject(subject,cb));
            ZonedDateTime time=ZonedDateTime.now().plusMinutes(5);LessonEntity lesson=new LessonEntity();lesson.subjectId=subjectId;lesson.dayOfWeek=time.getDayOfWeek().getValue();
            lesson.startMinute=time.getHour()*60+time.getMinute();lesson.endMinute=lesson.startMinute+1;lesson.room="314";
            this.<Long>call(cb->app.data().saveLesson(lesson,cb));main(()->app.reminders().setMinutes(5));
            NotificationManager manager=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
            boolean shown=false;long deadline=SystemClock.uptimeMillis()+25000;
            while(SystemClock.uptimeMillis()<deadline){for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications())if("UniPlan notification check".contentEquals(n.getNotification().extras.getCharSequence("android.title","")))shown=true;if(shown)break;SystemClock.sleep(100);}
            assertTrue("Real scheduled notification was shown",shown);
            UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            try(android.os.ParcelFileDescriptor command=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("cmd statusbar expand-notifications")){
                new java.io.FileInputStream(command.getFileDescriptor()).readAllBytes();
            }
            androidx.test.uiautomator.UiObject2 notification=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.pkg("com.android.systemui").text("UniPlan notification check")),10000);
            assertNotNull("Notification is visible in Android notification shade",notification);
            device.takeScreenshot(new File(app.getExternalFilesDir(null),"features-notification.png"));notification.click();find(R.id.easyAdd);
        }
    }
    private static void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private <T>T call(Consumer<StudyRepository.Result<T>> action)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<T> value=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();main(()->action.accept((v,e)->{value.set(v);error.set(e);latch.countDown();}));assertTrue(latch.await(15,TimeUnit.SECONDS));assertNull(error.get());return value.get();}
    private androidx.test.uiautomator.UiObject2 find(int id){
        String resource=app.getResources().getResourceEntryName(id);UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        androidx.test.uiautomator.UiObject2 view=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res(app.getPackageName(),resource)),15000);
        if(view==null){device.takeScreenshot(new File(app.getExternalFilesDir(null),"features-failure.png"));try{device.dumpWindowHierarchy(new File(app.getExternalFilesDir(null),"features-failure.xml"));}catch(java.io.IOException ignored){}}
        assertNotNull("Missing view "+resource,view);return view;
    }
    private void waitEnabled(int id){long end=SystemClock.uptimeMillis()+15000;do{if(find(id).isEnabled())return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<end);fail("View never enabled");}
    private void tap(int id){waitEnabled(id);find(id).click();UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle();}
}
