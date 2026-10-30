package kz.uniplan.app.ui.tasks;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentTasksBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public class TasksFragment extends BaseFragment {
    private FragmentTasksBinding b;
    private StudyViewModel vm;
    private TabLayoutMediator mediator;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (b != null && vm != null) { vm.notifyState(); clock.postDelayed(this, 60000); } }
    };
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state) {
        b=FragmentTasksBinding.inflate(inflater,container,false); return b.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view,Bundle state) { super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        b.searchInput.setText(vm.text("search"));
        Ui.watch(b.searchInput,s -> { vm.set("search",s); vm.notifyState(); });
        b.addButton.setOnClickListener(v -> Ui.go(this,R.id.taskEditFragment,0));
        b.sortButton.setText(vm.flag("prioritySort") ? R.string.core_sort_priority : R.string.core_sort_due);
        b.sortButton.setOnClickListener(v -> {
            vm.set("prioritySort",!vm.flag("prioritySort"));
            b.sortButton.setText(vm.flag("prioritySort") ? R.string.core_sort_priority : R.string.core_sort_due);
            vm.notifyState();
        });
        b.filterButton.setText(vm.text("filterName").isEmpty() ? getString(R.string.core_filter_all) : vm.text("filterName"));
        b.filterButton.setOnClickListener(v -> {
            Bundle args=new Bundle(); args.putString("resultKey","task_filter"); args.putBoolean("allowNone",true);
            NavHostFragment.findNavController(this).navigate(R.id.subjectPickerFragment,args);
        });
        getParentFragmentManager().setFragmentResultListener("task_filter",getViewLifecycleOwner(),(key,result) -> {
            vm.set("filter",result.getLong("subjectId"));
            vm.set("filterName",result.getLong("subjectId")==0 ? "" : result.getString("subjectName",""));
            b.filterButton.setText(vm.text("filterName").isEmpty() ? getString(R.string.core_filter_all) : vm.text("filterName"));
            vm.notifyState();
        });
        b.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override public Fragment createFragment(int position) { return TaskPageFragment.create(position); }
            @Override public int getItemCount() { return 3; }
        });
        int[] tabs={R.string.core_all,R.string.core_active,R.string.core_completed};
        mediator=new TabLayoutMediator(b.tabs,b.pager,(tab,position)->tab.setText(tabs[position])); mediator.attach();
        vm.errors.observe(getViewLifecycleOwner(),event -> { String error=event.take(); if (error!=null) message(error); });
        vm.deletedTask.observe(getViewLifecycleOwner(),event -> {
            var task=event.take(); if (task==null) return;
            Snackbar.make(b.getRoot(),R.string.deleted,Snackbar.LENGTH_LONG).setAction(R.string.undo,v -> vm.restore(task)).show();
        });
    }
    @Override public void onResume() { super.onResume(); clock.post(tick); }
    @Override public void onPause() { clock.removeCallbacks(tick); super.onPause(); }
    @Override public void onDestroyView() { clock.removeCallbacks(tick); if(mediator!=null) mediator.detach(); mediator=null; b.pager.setAdapter(null); b=null; super.onDestroyView(); }
}
