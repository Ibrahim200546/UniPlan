package kz.uniplan.app.ui.common;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.appcompat.app.AppCompatActivity;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.util.Appearance;
public abstract class BaseActivity extends AppCompatActivity {
    private View appearanceRoot;
    private ViewTreeObserver.OnGlobalLayoutListener appearanceListener;
    private void lifecycle(String event) { if(BuildConfig.DEBUG) Log.d("UniPlanLifecycle",getClass().getSimpleName()+":"+event); }
    @Override protected void onCreate(Bundle state) { super.onCreate(state); lifecycle("onCreate"); }
    @Override public void onContentChanged() {
        super.onContentChanged(); detachAppearance();
        appearanceRoot=findViewById(android.R.id.content);
        if(appearanceRoot instanceof ViewGroup){ViewGroup content=(ViewGroup)appearanceRoot;for(int index=0;index<content.getChildCount();index++)Appearance.applyRoot(content.getChildAt(index));}
        if(appearanceRoot!=null){appearanceListener=()->Appearance.applyActivity(this);appearanceRoot.getViewTreeObserver().addOnGlobalLayoutListener(appearanceListener);Appearance.applyActivity(this);}
    }
    @Override protected void onStart() { super.onStart(); lifecycle("onStart"); }
    @Override protected void onResume() { super.onResume(); lifecycle("onResume"); }
    @Override protected void onPause() { lifecycle("onPause"); super.onPause(); }
    @Override protected void onStop() { lifecycle("onStop"); super.onStop(); }
    @Override protected void onRestart() { super.onRestart(); lifecycle("onRestart"); }
    private void detachAppearance(){if(appearanceRoot!=null&&appearanceListener!=null&&appearanceRoot.getViewTreeObserver().isAlive())appearanceRoot.getViewTreeObserver().removeOnGlobalLayoutListener(appearanceListener);appearanceRoot=null;appearanceListener=null;}
    @Override protected void onDestroy() { detachAppearance(); lifecycle("onDestroy"); super.onDestroy(); }
}
