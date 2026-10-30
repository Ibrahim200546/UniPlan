package kz.uniplan.app.ui.subjects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentSubjectListBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.StudyViewModel;

public class SubjectPickerFragment extends BaseFragment {
    private FragmentSubjectListBinding b;
    private SubjectAdapter adapter;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentSubjectListBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        StudyViewModel vm=new ViewModelProvider(this).get(StudyViewModel.class);
        adapter=new SubjectAdapter();b.list.setAdapter(adapter);
        b.noneButton.setVisibility(getArguments()!=null&&getArguments().getBoolean("allowNone")?View.VISIBLE:View.GONE);
        if(getArguments()!=null&&"task_filter".equals(getArguments().getString("resultKey")))b.noneButton.setText(R.string.core_filter_all);
        b.noneButton.setOnClickListener(v->select(0,""));
        b.addButton.setOnClickListener(v->Ui.go(this,R.id.subjectEditFragment,0));
        b.list.setOnItemClickListener((parent,row,position,id)->select(id,adapter.getItem(position).name));
        vm.subjects.observe(getViewLifecycleOwner(),subjects->{adapter.submit(subjects);b.emptyText.setVisibility(subjects==null||subjects.isEmpty()?View.VISIBLE:View.GONE);});
    }
    private void select(long id,String name){
        String key=getArguments()==null?"subject_pick":getArguments().getString("resultKey","subject_pick");
        Bundle result=new Bundle();result.putLong("subjectId",id);result.putString("subjectName",name);
        getParentFragmentManager().setFragmentResult(key,result);NavHostFragment.findNavController(this).navigateUp();
    }
    @Override public void onDestroyView(){b.list.setAdapter(null);adapter=null;b=null;super.onDestroyView();}
}
