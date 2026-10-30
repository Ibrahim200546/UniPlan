package kz.uniplan.app.ui.common;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import kz.uniplan.app.BuildConfig;
import kz.uniplan.app.UniPlanApp;
import kz.uniplan.app.util.Appearance;
public abstract class BaseFragment extends Fragment {
    protected UniPlanApp app() { return UniPlanApp.get(requireContext()); }
    protected void message(String error) { if(getView()!=null) Snackbar.make(requireView(),Ui.error(requireContext(),error),Snackbar.LENGTH_LONG).show(); }
    private void lifecycle(String event) { if(BuildConfig.DEBUG) Log.d("UniPlanLifecycle",getClass().getSimpleName()+":"+event); }
    @Override public void onAttach(Context context) { super.onAttach(context); lifecycle("onAttach"); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); lifecycle("onCreate"); }
    @Override public void onViewCreated(View view,Bundle state) { super.onViewCreated(view,state); Appearance.applyRoot(view); lifecycle("onViewCreated"); }
    @Override public void onStart() { super.onStart(); lifecycle("onStart"); }
    @Override public void onResume() { super.onResume(); lifecycle("onResume"); }
    @Override public void onPause() { lifecycle("onPause"); super.onPause(); }
    @Override public void onStop() { lifecycle("onStop"); super.onStop(); }
    @Override public void onDestroyView() { lifecycle("onDestroyView"); super.onDestroyView(); }
    @Override public void onDestroy() { lifecycle("onDestroy"); super.onDestroy(); }
    @Override public void onDetach() { lifecycle("onDetach"); super.onDetach(); }
}
