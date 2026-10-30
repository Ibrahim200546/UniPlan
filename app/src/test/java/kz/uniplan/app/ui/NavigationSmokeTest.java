package kz.uniplan.app.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.ui.notes.NoteEditFragment;
import kz.uniplan.app.ui.notes.NoteEditorFragment;
import kz.uniplan.app.ui.notes.NotePreviewFragment;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/**
 * Runs real Activity/Fragment/layout code on Robolectric's Android 28 runtime.
 * These checks cover navigation wiring and saved form state, not device rendering
 * or the behavior of a real document provider/Firebase server.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class NavigationSmokeTest {
    private ActivityController<MainActivity> controller;
    private UniPlanApp app;

    @Before public void launchSignedInDemo() {
        Assume.assumeTrue("These scenarios require the explicit demo flavor", "demo".equals(BuildConfig.FLAVOR));
        app = ApplicationProvider.getApplicationContext();
        assertSame("Robolectric test actions run on the main thread", Looper.getMainLooper(), Looper.myLooper());
        app.auth().signOut();
        app.auth().openDemo(1);
        assertNotNull(app.auth().current());
        Intent intent = new Intent(app, MainActivity.class).putExtra("greeting", "Demo");
        controller = Robolectric.buildActivity(MainActivity.class, intent).setup().visible();
        drain();
        assertFalse(activity().isFinishing());
        assertEquals(R.id.homeFragment, nav().getCurrentDestination().getId());
    }

    @After public void destroyActivity() {
        if (controller != null) {
            controller.pause().stop().destroy();
            controller = null;
        }
        if (app != null) app.auth().signOut();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void fiveSectionsInflateAndTaskTabsRemainWiredAfterReturning() {
        int[] destinations = {R.id.homeFragment, R.id.tasksFragment, R.id.scheduleFragment,
                R.id.notesFragment, R.id.profileFragment};
        int[] requiredViews = {R.id.welcomeText, R.id.pager, R.id.daySpinner, R.id.notesList, R.id.saveProfile};
        for (int index = 0; index < destinations.length; index++) {
            selectTab(destinations[index]);
            assertNotNull("Selected section must inflate its real layout", current().requireView().findViewById(requiredViews[index]));
            assertEquals(View.VISIBLE, activity().findViewById(R.id.bottomNav).getVisibility());
        }
        // Re-entering a retained tab must recreate its view and reattach one mediator.
        selectTab(R.id.tasksFragment);
        ViewPager2 pager = current().requireView().findViewById(R.id.pager);
        TabLayout tabs = current().requireView().findViewById(R.id.tabs);
        assertNotNull(pager.getAdapter());
        assertEquals(3, pager.getAdapter().getItemCount());
        assertEquals(3, tabs.getTabCount());
        pager.setCurrentItem(2, false);
        drain();
        assertEquals(2, tabs.getSelectedTabPosition());
        tabs.getTabAt(0).select();
        drain();
        assertEquals(0, pager.getCurrentItem());
        selectTab(R.id.notesFragment);
        selectTab(R.id.tasksFragment);
        assertEquals(3, ((TabLayout) current().requireView().findViewById(R.id.tabs)).getTabCount());
    }

    @Test public void nestedFormsAndListViewPickerInflateAndReturnToParent() {
        selectTab(R.id.profileFragment);
        current().requireView().findViewById(R.id.subjects).performClick();
        drain();
        assertEquals(R.id.subjectListFragment, nav().getCurrentDestination().getId());
        assertTrue(current().requireView().findViewById(R.id.list) instanceof ListView);
        current().requireView().findViewById(R.id.addButton).performClick();
        drain();
        assertEquals(R.id.subjectEditFragment, nav().getCurrentDestination().getId());
        assertNotNull(current().requireView().findViewById(R.id.nameInput));
        assertTrue(nav().navigateUp());
        drain();
        assertTrue(nav().navigateUp());
        drain();

        selectTab(R.id.tasksFragment);
        current().requireView().findViewById(R.id.addButton).performClick();
        drain();
        assertEquals(R.id.taskEditFragment, nav().getCurrentDestination().getId());
        assertEquals(View.GONE, activity().findViewById(R.id.bottomNav).getVisibility());
        current().requireView().findViewById(R.id.subjectButton).performClick();
        drain();
        assertEquals(R.id.subjectPickerFragment, nav().getCurrentDestination().getId());
        assertTrue(current().requireView().findViewById(R.id.list) instanceof ListView);
        assertTrue(nav().navigateUp());
        drain();
        assertEquals(R.id.taskEditFragment, nav().getCurrentDestination().getId());
        assertTrue(nav().navigateUp());
        drain();

        selectTab(R.id.scheduleFragment);
        current().requireView().findViewById(R.id.addButton).performClick();
        drain();
        assertEquals(R.id.lessonEditFragment, nav().getCurrentDestination().getId());
        assertNotNull(current().requireView().findViewById(R.id.roomInput));
        assertNotNull(current().requireView().findViewById(R.id.startButton));
        assertTrue(nav().navigateUp());
        drain();
        assertEquals(R.id.scheduleFragment, nav().getCurrentDestination().getId());
    }

    @Test public void taskDraftRetainsTextAndSingleChoicesAcrossActivityRecreation() {
        navigate(R.id.taskEditFragment, 0);
        await(() -> view(R.id.titleInput).isEnabled());
        ((EditText) view(R.id.titleInput)).setText("Әзірленіп жатқан тапсырма");
        ((EditText) view(R.id.descriptionInput)).setText("Сақтамай бұрғанда да осы мәтін қалуы керек.");
        ((RadioButton) view(R.id.exam)).setChecked(true);
        ((RadioButton) view(R.id.high)).setChecked(true);
        ((CheckBox) view(R.id.completed)).setChecked(true);
        String due = ((TextView) view(R.id.dueButton)).getText().toString();
        MainActivity original = activity();
        controller.recreate();
        drain();
        assertNotSame(original, activity());
        assertEquals(R.id.taskEditFragment, nav().getCurrentDestination().getId());
        assertEquals("Әзірленіп жатқан тапсырма", ((EditText) view(R.id.titleInput)).getText().toString());
        assertEquals("Сақтамай бұрғанда да осы мәтін қалуы керек.", ((EditText) view(R.id.descriptionInput)).getText().toString());
        assertTrue(((RadioButton) view(R.id.exam)).isChecked());
        assertFalse(((RadioButton) view(R.id.assignment)).isChecked());
        assertTrue(((RadioButton) view(R.id.high)).isChecked());
        assertFalse(((RadioButton) view(R.id.normal)).isChecked());
        assertTrue(((CheckBox) view(R.id.completed)).isChecked());
        assertEquals(due, ((TextView) view(R.id.dueButton)).getText().toString());
    }

    @Test public void personalLessonDetailsAndDraftSurviveRecreationAndSave() {
        selectTab(R.id.scheduleFragment);
        await(() -> app.data().lessons().getValue() != null && app.data().lessons().getValue().size() >= 23);
        kz.uniplan.app.data.local.entity.LessonEntity lesson = app.data().lessons().getValue().stream()
                .filter(item -> item.dayOfWeek == 1 && item.lessonNumber == 1).findFirst().get();
        navigate(R.id.lessonEditFragment, lesson.id);
        await(() -> view(R.id.linkInput).isEnabled());
        assertEquals("1", ((TextView) view(R.id.numberInput)).getText().toString());
        assertEquals("Дәріс", ((TextView) view(R.id.typeInput)).getText().toString());
        assertEquals("http://meet.google.com/mhi-wnyv-ind", ((TextView) view(R.id.linkInput)).getText().toString());
        assertTrue(((TextView) view(R.id.lessonTeacherInput)).getText().toString().contains("Оразбаева Асель Айтмухамедовна"));
        ((EditText) view(R.id.roomInput)).setText("314 — тексеру");
        ((EditText) view(R.id.numberInput)).setText("2");
        controller.recreate(); drain();
        assertEquals("2", ((TextView) view(R.id.numberInput)).getText().toString());
        assertEquals("314 — тексеру", ((TextView) view(R.id.roomInput)).getText().toString());
        assertEquals(lesson.teacher, ((TextView) view(R.id.lessonTeacherInput)).getText().toString());
        assertEquals(lesson.meetingUrl, ((TextView) view(R.id.linkInput)).getText().toString());
        view(R.id.saveButton).performClick();
        await(() -> nav().getCurrentDestination().getId() == R.id.scheduleFragment);
        navigate(R.id.lessonEditFragment, lesson.id);
        await(() -> view(R.id.linkInput).isEnabled());
        assertEquals("314 — тексеру", ((TextView) view(R.id.roomInput)).getText().toString());
        assertEquals(lesson.teacher, ((TextView) view(R.id.lessonTeacherInput)).getText().toString());
        assertEquals(lesson.meetingUrl, ((TextView) view(R.id.linkInput)).getText().toString());
        // Return the fixture to its original values for other scenarios using the app singleton.
        ((EditText) view(R.id.roomInput)).setText(lesson.room);
        ((EditText) view(R.id.numberInput)).setText("1");
        view(R.id.saveButton).performClick();
        await(() -> nav().getCurrentDestination().getId() == R.id.scheduleFragment);
    }

    @Test public void notePreviewAndUnsavedDraftSurviveRecreationWithoutDuplicateChildren() {
        selectTab(R.id.notesFragment);
        current().requireView().findViewById(R.id.addNote).performClick();
        drain();
        assertEquals(R.id.noteEditFragment, nav().getCurrentDestination().getId());
        await(() -> view(R.id.noteTitle).isEnabled());
        ((EditText) view(R.id.noteTitle)).setText("Қазақша конспект");
        ((EditText) view(R.id.noteContent)).setText("Ә Ғ Қ Ң Ө Ұ Ү Һ І\nЕкінші жол.");
        view(R.id.notePreview).performClick();
        drain();
        assertTrue(noteChild() instanceof NotePreviewFragment);
        assertEquals("Ә Ғ Қ Ң Ө Ұ Ү Һ І\nЕкінші жол.", ((TextView) view(R.id.previewBody)).getText().toString());

        controller.recreate();
        drain();
        assertEquals(R.id.noteEditFragment, nav().getCurrentDestination().getId());
        NoteEditFragment parent = (NoteEditFragment) current();
        assertEquals(1, parent.getChildFragmentManager().getBackStackEntryCount());
        assertTrue(noteChild() instanceof NotePreviewFragment);
        long visibleChildren = parent.getChildFragmentManager().getFragments().stream()
                .filter(fragment -> fragment.getView() != null && fragment.isAdded()).count();
        assertEquals("Only the restored preview owns a current child view", 1L, visibleChildren);
        assertEquals("Қазақша конспект", ((EditText) view(R.id.noteTitle)).getText().toString());

        assertTrue(activity().onSupportNavigateUp());
        drain();
        assertEquals(R.id.noteEditFragment, nav().getCurrentDestination().getId());
        assertTrue(noteChild() instanceof NoteEditorFragment);
        assertEquals(0, current().getChildFragmentManager().getBackStackEntryCount());
        assertEquals("Ә Ғ Қ Ң Ө Ұ Ү Һ І\nЕкінші жол.", ((EditText) view(R.id.noteContent)).getText().toString());
        view(R.id.notePreview).performClick();
        drain();
        activity().getOnBackPressedDispatcher().onBackPressed();
        drain();
        assertEquals(R.id.noteEditFragment, nav().getCurrentDestination().getId());
        assertTrue(noteChild() instanceof NoteEditorFragment);
    }

    private MainActivity activity() { return controller.get(); }
    private NavHostFragment host() { return (NavHostFragment) activity().getSupportFragmentManager().findFragmentById(R.id.navHost); }
    private NavController nav() { return host().getNavController(); }
    private Fragment current() { return host().getChildFragmentManager().getPrimaryNavigationFragment(); }
    private View view(int id) {
        View result = current().requireView().findViewById(id);
        assertNotNull("Expected current destination view id " + id, result);
        return result;
    }
    private Fragment noteChild() { return current().getChildFragmentManager().findFragmentById(R.id.noteChildContainer); }
    private void selectTab(int id) {
        ((BottomNavigationView) activity().findViewById(R.id.bottomNav)).setSelectedItemId(id);
        drain();
        assertEquals(id, nav().getCurrentDestination().getId());
    }
    private void navigate(int destination, long id) {
        Bundle args = new Bundle(); args.putLong("id", id);
        nav().navigate(destination, args);
        drain();
        assertEquals(destination, nav().getCurrentDestination().getId());
    }
    private void drain() {
        shadowOf(Looper.getMainLooper()).idle();
        if (controller != null) flush(activity().getSupportFragmentManager());
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void flush(FragmentManager manager) {
        if (manager.isDestroyed()) return;
        manager.executePendingTransactions();
        for (Fragment fragment : new ArrayList<>(manager.getFragments())) {
            if (fragment.isAdded()) flush(fragment.getChildFragmentManager());
        }
    }
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            drain();
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        }
        drain();
        assertTrue("UI condition did not become ready within five seconds", condition.getAsBoolean());
    }
}
