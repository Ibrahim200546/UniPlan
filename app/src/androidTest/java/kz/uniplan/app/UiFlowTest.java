package kz.uniplan.app;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.View;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.util.TreeIterables;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import kz.uniplan.app.data.auth.AuthRepository;
import kz.uniplan.app.data.files.NoteFiles;
import kz.uniplan.app.data.local.AppDatabase;
import kz.uniplan.app.data.local.dao.StudyDao;
import kz.uniplan.app.data.local.entity.NoteEntity;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.data.repository.StudyRepository;
import kz.uniplan.app.ui.MainActivity;
import kz.uniplan.app.ui.tasks.StudyViewModel;

/** Real UI actions plus independent database assertions. No fixed success delays. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UiFlowTest {
    private static final String TEST_OWNER = "demo-2";
    private UniPlanApp app;
    private StudyDao dao;
    private String token;
    private AuthRepository.Session previousSession;
    private long subjectId;
    private String subjectName;

    @Before public void setup() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        assumeTrue("UI fixtures require the explicit demo flavor", app.auth().isDemo());
        token = "UIT-" + System.nanoTime();
        dao = AppDatabase.get(app).studyDao();
        main(() -> { previousSession = app.auth().current(); app.auth().openDemo(2); });
        SubjectEntity subject = new SubjectEntity();
        subjectName = token + " пән";
        subject.name = subjectName; subject.teacher = "UI test"; subject.color = 0xFF52796F;
        subjectId = call(callback -> app.data().saveSubject(subject, callback));
    }

    @After public void cleanup() {
        if (dao == null || token == null) return;
        // Only records created by this test token are removed; existing demo data remains.
        for (TaskEntity task : dao.tasks(TEST_OWNER)) if (task.title.startsWith(token)) dao.deleteTask(TEST_OWNER, task.id);
        for (NoteEntity note : dao.notes(TEST_OWNER)) if (note.title.startsWith(token)) {
            removeCopy(app.getFilesDir(), note.id);
            File external = app.getExternalFilesDir(null);
            if (external != null) removeCopy(external, note.id);
            dao.deleteNote(TEST_OWNER, note.id);
        }
        if (subjectId != 0) dao.deleteSubject(TEST_OWNER, subjectId);
        main(() -> {
            if (previousSession == null) app.auth().signOut();
            else app.auth().openDemo("demo-1".equals(previousSession.uid) ? 1 : 2);
        });
    }

    @Test public void taskFlowSurvivesRotationAndUndoRestoresExactlyOneRecord() throws Exception {
        String title = token + " тапсырма";
        String changed = title + " түзету";
        String description = "Ә, Ғ, Қ, Ң, Ө, Ұ, Ү, Һ, І — форма сақталады.";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Each of the five actual bottom-navigation destinations is visited.
            waitFor(allOf(withId(R.id.welcomeText), isDisplayed()));
            tap(R.id.scheduleFragment); waitFor(allOf(withId(R.id.daySpinner), isDisplayed()));
            tap(R.id.notesFragment); waitFor(allOf(withId(R.id.addNote), isDisplayed()));
            tap(R.id.profileFragment); waitFor(allOf(withId(R.id.name), isDisplayed()));
            tap(R.id.homeFragment); waitFor(allOf(withId(R.id.welcomeText), isDisplayed()));
            tap(R.id.tasksFragment); waitFor(allOf(withId(R.id.searchInput), isDisplayed()));
            tap(R.id.addButton);
            waitFor(allOf(withId(R.id.titleInput), isEnabled()));
            onView(withId(R.id.titleInput)).perform(reveal(), replaceText(title), closeSoftKeyboard());
            onView(withId(R.id.descriptionInput)).perform(reveal(), replaceText(description), closeSoftKeyboard());
            tap(R.id.subjectButton);
            onData(subjectNamed(subjectName)).inAdapterView(withId(R.id.list)).perform(click());
            tap(R.id.exam); tap(R.id.high);

            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE);
            onView(withId(R.id.titleInput)).check(matches(withText(title)));
            onView(withId(R.id.descriptionInput)).check(matches(withText(description)));
            onView(withId(R.id.subjectButton)).check(matches(withText(subjectName)));
            onView(withId(R.id.exam)).check(matches(isChecked()));
            onView(withId(R.id.high)).check(matches(isChecked()));
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT);

            tap(R.id.saveButton);
            waitFor(allOf(withId(R.id.searchInput), isDisplayed()));
            onView(withId(R.id.searchInput)).perform(replaceText(title), closeSoftKeyboard());
            waitFor(allOf(withText(title), isDisplayed()));
            onView(allOf(withText(title), isDisplayed())).perform(click());
            waitFor(allOf(withId(R.id.titleText), withText(title), isDisplayed()));
            tap(R.id.completed);
            waitFor(allOf(withId(R.id.completed), isChecked()));
            TaskEntity saved = task(title);
            assertTrue(saved.completed); assertEquals(1, saved.kind); assertEquals(2, saved.priority);
            assertEquals(subjectId, saved.subjectId); assertEquals(description, saved.description);
            long id = saved.id;

            tap(R.id.editButton);
            waitFor(allOf(withId(R.id.titleInput), isEnabled()));
            onView(withId(R.id.titleInput)).perform(reveal(), replaceText(changed), closeSoftKeyboard());
            tap(R.id.saveButton);
            waitFor(allOf(withId(R.id.titleText), withText(changed), isDisplayed()));
            tap(R.id.deleteButton);
            onView(withText(R.string.undo)).perform(click());
            waitFor(allOf(withText(changed), isDisplayed()));
            long count = dao.tasks(TEST_OWNER).stream().filter(item -> item.title.equals(changed)).count();
            assertEquals(1L, count);
            assertEquals(id, task(changed).id);
        }
    }

    @Test public void notePreviewAndCopiesPreserveUtf8WithoutSilentlyOverwritingRoom() throws Exception {
        String title = token + " жазба";
        String original = "Ә Ғ Қ Ң Ө Ұ Ү Һ І\nКөшірме: қазақша мәтін.";
        String newer = "Room: жаңартылған негізгі мәтін.";
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            tap(R.id.notesFragment); tap(R.id.addNote);
            waitFor(allOf(withId(R.id.noteTitle), isEnabled()));
            onView(withId(R.id.noteTitle)).perform(reveal(), replaceText(title), closeSoftKeyboard());
            onView(withId(R.id.noteContent)).perform(reveal(), replaceText(original), closeSoftKeyboard());
            tap(R.id.noteSave); waitFor(allOf(withId(R.id.noteStatus), withText(R.string.notes_saved_state)));
            long id = note(title).id;
            tap(R.id.notePreview);
            waitFor(allOf(withId(R.id.previewBody), withText(original)));
            pressBack(); // Child back stack returns to the editor before leaving the note.
            waitFor(allOf(withId(R.id.noteContent), withText(original)));

            copyAction(R.string.notes_internal_save);
            assertEquals(original, this.<String>call(callback -> app.files().read(id, false, callback)));
            onView(withId(R.id.noteContent)).perform(reveal(), replaceText(newer), closeSoftKeyboard());
            tap(R.id.noteSave); waitFor(allOf(withId(R.id.noteStatus), withText(R.string.notes_saved_state)));
            assertEquals(newer, dao.note(TEST_OWNER, id).content);

            copyAction(R.string.notes_internal_open); tap(android.R.id.button1);
            waitFor(allOf(withId(R.id.noteContent), withText(original)));
            assertEquals("Opening a file only changes the draft", newer, dao.note(TEST_OWNER, id).content);
            tap(R.id.noteSave); waitFor(allOf(withId(R.id.noteStatus), withText(R.string.notes_saved_state)));
            assertEquals(original, dao.note(TEST_OWNER, id).content);

            copyAction(R.string.notes_external_save);
            assertEquals(original, this.<String>call(callback -> app.files().read(id, true, callback)));
            onView(withId(R.id.noteContent)).perform(reveal(), replaceText("Уақытша өзгеріс"), closeSoftKeyboard());
            copyAction(R.string.notes_external_open); tap(android.R.id.button1);
            waitFor(allOf(withId(R.id.noteContent), withText(original)));
            assertEquals(original, dao.note(TEST_OWNER, id).content);
        }
    }

    @Test public void oldViewModelCannotSaveAfterSigningIntoTheSameDemoUidAgain() {
        AtomicReference<String> error = new AtomicReference<>();
        main(() -> {
            StudyViewModel stale = new StudyViewModel(app, new SavedStateHandle());
            stale.loadTask(0); stale.set("title", token + " stale"); stale.set("subject", subjectId);
            AuthRepository.Session previousEpoch = app.auth().current();
            app.auth().signOut(); app.auth().openDemo(2);
            assertEquals(previousEpoch.uid, app.auth().current().uid);
            assertNotSame(previousEpoch, app.auth().current());
            stale.saveTask();
            StudyViewModel.Event<String> event = stale.errors.getValue();
            error.set(event == null ? null : event.take());
        });
        assertEquals("session", error.get());
        assertFalse(dao.tasks(TEST_OWNER).stream().anyMatch(item -> item.title.equals(token + " stale")));
    }

    private void copyAction(int textResource) { tap(R.id.noteCopies); onView(withText(textResource)).perform(click()); }
    private TaskEntity task(String title) { return dao.tasks(TEST_OWNER).stream().filter(item -> item.title.equals(title)).findFirst().orElseThrow(() -> new AssertionError("Task was not saved")); }
    private NoteEntity note(String title) { return dao.notes(TEST_OWNER).stream().filter(item -> item.title.equals(title)).findFirst().orElseThrow(() -> new AssertionError("Note was not saved")); }
    private void removeCopy(File root, long id) { new AtomicFile(new File(new File(new File(root, "note_copies"), NoteFiles.ownerDirectory(TEST_OWNER)), "note_" + id + ".txt")).delete(); }
    private static void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private <T> T call(Consumer<StudyRepository.Result<T>> operation) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        main(() -> operation.accept((result, failure) -> { value.set(result); error.set(failure); latch.countDown(); }));
        assertTrue("Async operation timed out", latch.await(15, TimeUnit.SECONDS));
        assertNull("Unexpected repository/file error", error.get());
        return value.get();
    }
    private static Matcher<Object> subjectNamed(String name) {
        return new TypeSafeMatcher<Object>() {
            @Override public void describeTo(Description description) { description.appendText("Subject named " + name); }
            @Override protected boolean matchesSafely(Object item) { return item instanceof SubjectEntity && ((SubjectEntity)item).name.equals(name); }
        };
    }
    private static void tap(int id) {
        waitFor(allOf(withId(id), isEnabled()));
        onView(withId(id)).perform(reveal(), click());
    }
    /** Supports the app's NestedScrollView forms as well as ordinary visible views. */
    private static ViewAction reveal() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return org.hamcrest.Matchers.any(View.class); }
            @Override public String getDescription() { return "bring control into its scroll viewport"; }
            @Override public void perform(UiController controller, View view) {
                view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
                controller.loopMainThreadUntilIdle();
            }
        };
    }
    private static void waitFor(Matcher<View> target) {
        onView(isRoot()).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isRoot(); }
            @Override public String getDescription() { return "wait up to 15 seconds for " + target; }
            @Override public void perform(UiController controller, View root) {
                long deadline = SystemClock.uptimeMillis() + 15000;
                do {
                    for (View child : TreeIterables.breadthFirstViewTraversal(root)) if (target.matches(child)) return;
                    controller.loopMainThreadForAtLeast(50);
                } while (SystemClock.uptimeMillis() < deadline);
                throw new AssertionError("UI did not reach expected state: " + target);
            }
        });
    }
    private static void rotate(ActivityScenario<MainActivity> scenario, int requested, int configuration) {
        scenario.onActivity(activity -> activity.setRequestedOrientation(requested));
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicInteger actual = new AtomicInteger();
        do {
            scenario.onActivity(activity -> actual.set(activity.getResources().getConfiguration().orientation));
            if (actual.get() == configuration) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Device did not rotate to requested orientation");
    }
}
