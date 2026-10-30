package kz.uniplan.app.ui.subjects;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentSubjectListBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.StudyViewModel;

public class SubjectListFragment extends BaseFragment {
    private FragmentSubjectListBinding b;
    private SubjectAdapter adapter;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentSubjectListBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        StudyViewModel vm=new ViewModelProvider(this).get(StudyViewModel.class);
        adapter=new SubjectAdapter();b.list.setAdapter(adapter);b.noneButton.setVisibility(View.GONE);
        b.addButton.setOnClickListener(v->Ui.go(this,R.id.subjectEditFragment,0));
        b.list.setOnItemClickListener((parent,row,position,id)->Ui.go(this,R.id.subjectEditFragment,id));
        vm.subjects.observe(getViewLifecycleOwner(),subjects->{adapter.submit(subjects);b.emptyText.setVisibility(subjects==null||subjects.isEmpty()?View.VISIBLE:View.GONE);});
    }
    @Override public void onDestroyView(){b.list.setAdapter(null);adapter=null;b=null;super.onDestroyView();}
}
