package kz.uniplan.app.ui.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.databinding.FragmentTaskPageBinding;
import kz.uniplan.app.domain.model.StudyItem;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public class TaskPageFragment extends BaseFragment {
    private FragmentTaskPageBinding b;
    private StudyViewModel vm;
    private TaskAdapter adapter;
    public static TaskPageFragment create(int mode) { TaskPageFragment f=new TaskPageFragment(); Bundle b=new Bundle();b.putInt("mode",mode);f.setArguments(b);return f; }
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state) { b=FragmentTaskPageBinding.inflate(inflater,container,false);return b.getRoot(); }
    @Override public void onViewCreated(@NonNull View view,Bundle state) { super.onViewCreated(view,state);
        vm=new ViewModelProvider(requireParentFragment()).get(StudyViewModel.class);
        adapter=new TaskAdapter(new TaskAdapter.Actions() {
            public void open(long id) { Ui.go(TaskPageFragment.this,R.id.taskDetailFragment,id); }
            public void complete(long id,boolean completed) { vm.complete(id,completed); }
            public void delete(long id) { vm.deleteTask(id); }
        },false);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext())); b.list.setAdapter(adapter);
        vm.tasks.observe(getViewLifecycleOwner(),items->render());
        vm.subjects.observe(getViewLifecycleOwner(),items->render());
        vm.revision.observe(getViewLifecycleOwner(),v->render());
    }
    private void render() {
        if(b==null) return;
        List<TaskEntity> source=vm.tasks.getValue(); b.progress.setVisibility(source==null?View.VISIBLE:View.GONE);
        List<TaskEntity> visible=new ArrayList<>();
        int mode=requireArguments().getInt("mode"); String query=vm.text("search").trim().toLowerCase(Locale.ROOT);
        if(source!=null) for(TaskEntity t:source) {
            if(mode==1&&t.completed || mode==2&&!t.completed) continue;
            if(vm.number("filter",0)!=0 && t.subjectId!=vm.number("filter",0)) continue;
            if(!t.title.toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(t);
        }
        visible.sort(vm.flag("prioritySort")?StudyItem.BY_PRIORITY:StudyItem.BY_DUE);
        adapter.submit(visible,vm.subjects.getValue());
        b.emptyText.setVisibility(source!=null&&visible.isEmpty()?View.VISIBLE:View.GONE);
    }
    @Override public void onResume() { super.onResume(); if(vm!=null) render(); }
    @Override public void onDestroyView() { b.list.setAdapter(null);adapter=null;b=null;super.onDestroyView(); }
}
