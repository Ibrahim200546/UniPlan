package kz.uniplan.app.util;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.lifecycle.SavedStateHandle;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.material.card.MaterialCardView;
import java.util.HashMap;
import java.util.Map;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.R;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.ui.MainActivity;
import kz.uniplan.app.ui.profile.AppearanceViewModel;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class AppearanceSettingsTest {
    private UniPlanApp app;
    @Before public void setup(){app=ApplicationProvider.getApplicationContext();app.getSharedPreferences("device_settings",Context.MODE_PRIVATE).edit().clear().commit();}

    @Test public void savedLightAndDarkPalettesSurviveNewStoreAndResetIndependently(){
        SettingsStore first=new SettingsStore(app);
        AppearancePalette light=new AppearancePalette(0xfffefefe,0xff123456,0xfffbcc11,6);
        AppearancePalette dark=new AppearancePalette(0xff010203,0xff223344,0xffabcdef,30);
        first.saveAppearance(false,light);first.saveAppearance(true,dark);first.setNoteGrid("appearance-test",true);
        SettingsStore reopened=new SettingsStore(app);
        assertEquals(light,reopened.appearance(false));assertEquals(dark,reopened.appearance(true));
        reopened.resetAppearance(false);
        assertEquals(AppearancePalette.defaults(false),new SettingsStore(app).appearance(false));
        assertEquals(dark,new SettingsStore(app).appearance(true));assertTrue(reopened.noteGrid("appearance-test"));
    }
    @Test public void draftRestorationKeepsBothThemesWithoutPersistingBeforeApply(){
        SavedStateHandle state=new SavedStateHandle();AppearanceViewModel model=new AppearanceViewModel(app,state);
        model.setNight(false);model.setHex("background","#112233");model.setHex("card","#FFEEDD");model.setCorner(27);
        model.setNight(true);model.setHex("accent","#99CCFF");model.setNight(false);
        Map<String,Object> snapshot=new HashMap<>();for(String key:state.keys())snapshot.put(key,state.get(key));
        AppearanceViewModel restored=new AppearanceViewModel(app,new SavedStateHandle(snapshot));
        assertEquals("#112233",restored.hex("background"));assertEquals("#FFEEDD",restored.hex("card"));assertEquals(27,restored.corner());
        assertEquals(AppearancePalette.defaults(false),app.prefs().appearance(false));
        restored.save();assertEquals(0xff112233,app.prefs().appearance(false).background);
        restored.setNight(true);assertEquals("#99CCFF",restored.hex("accent"));
    }
    @Test public void invalidHexKeepsPreviewAndCannotOverwriteSavedPalette(){
        AppearanceViewModel model=new AppearanceViewModel(app,new SavedStateHandle());model.setNight(false);
        AppearancePalette before=model.palette();model.setHex("card","#XYZ");
        assertFalse(model.valid());assertEquals(before,model.palette());
        try{model.save();fail("Invalid draft was persisted");}catch(IllegalStateException expected){}
        assertEquals(before,app.prefs().appearance(false));
    }
    @Test public void appearanceScreenKeepsHexDraftAcrossActivityRecreationAndAppliesInPlace(){
        Assume.assumeTrue("Demo UI scenario", "demo".equals(BuildConfig.FLAVOR));
        app.auth().openDemo(1);
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible();
        try{
            NavHostFragment host=(NavHostFragment)controller.get().getSupportFragmentManager().findFragmentById(R.id.navHost);
            host.getNavController().navigate(R.id.appearanceFragment);drain(controller);
            ((RadioButton)controller.get().findViewById(R.id.paletteLight)).performClick();
            ((EditText)controller.get().findViewById(R.id.backgroundHex)).setText("#102030");
            ((EditText)controller.get().findViewById(R.id.cardHex)).setText("#EEDDCC");
            controller.recreate();drain(controller);
            assertEquals("#102030",((EditText)controller.get().findViewById(R.id.backgroundHex)).getText().toString());
            assertEquals("#EEDDCC",((EditText)controller.get().findViewById(R.id.cardHex)).getText().toString());
            MainActivity sameActivity=controller.get();
            controller.get().findViewById(R.id.applyAppearance).performClick();drain(controller);
            assertSame("Applying colors does not recreate the activity",sameActivity,controller.get());
            assertEquals(0xff102030,new SettingsStore(app).appearance(false).background);
            assertEquals(0xffeeddcc,new SettingsStore(app).appearance(false).card);
            MaterialCardView card=new MaterialCardView(controller.get());
            LinearLayout content=new LinearLayout(controller.get());TextView title=new TextView(controller.get());
            View subjectStrip=new View(controller.get());subjectStrip.setBackgroundColor(0xffbc1234);
            content.addView(title);content.addView(subjectStrip);card.addView(content);Appearance.applyCard(card);
            assertEquals(0xffeeddcc,card.getCardBackgroundColor().getDefaultColor());
            assertTrue(AppearancePalette.contrast(title.getCurrentTextColor(),0xffeeddcc)>=4.5);
            assertEquals("Subject-owned colors must not be overwritten",0xffbc1234,((ColorDrawable)subjectStrip.getBackground()).getColor());
        }finally{controller.pause().stop().destroy();app.auth().signOut();}
    }
    private static void drain(ActivityController<MainActivity> controller){
        shadowOf(Looper.getMainLooper()).idle();controller.get().getSupportFragmentManager().executePendingTransactions();
        NavHostFragment host=(NavHostFragment)controller.get().getSupportFragmentManager().findFragmentById(R.id.navHost);
        if(host!=null)host.getChildFragmentManager().executePendingTransactions();shadowOf(Looper.getMainLooper()).idle();
    }
}
