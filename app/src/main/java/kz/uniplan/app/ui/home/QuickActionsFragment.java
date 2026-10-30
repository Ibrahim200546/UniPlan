package kz.uniplan.app.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import kz.uniplan.app.databinding.FragmentQuickActionsBinding;
import kz.uniplan.app.ui.common.BaseFragment;

/** A real child-to-parent callback. The child does not own the main NavHost. */
public class QuickActionsFragment extends BaseFragment {
    public interface Callback { void onCreateTask(); void onCreateNote(); }
    private Callback callback;
    private FragmentQuickActionsBinding b;
    @Override public void onAttach(@NonNull Context context){super.onAttach(context);callback=(Callback)requireParentFragment();}
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentQuickActionsBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);b.taskButton.setOnClickListener(v->callback.onCreateTask());b.noteButton.setOnClickListener(v->callback.onCreateNote());}
    @Override public void onDestroyView(){b=null;super.onDestroyView();}
    @Override public void onDetach(){callback=null;super.onDetach();}
}
