package kz.uniplan.app.ui.tasks;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import java.util.Calendar;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentTaskEditBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public class TaskEditFragment extends BaseFragment {
    private FragmentTaskEditBinding b;
    private StudyViewModel vm;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state) { b=FragmentTaskEditBinding.inflate(inflater,container,false);return b.getRoot(); }
    @Override public void onViewCreated(@NonNull View view,Bundle state) { super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);
        Ui.watch(b.titleInput,text->vm.set("title",text));
        Ui.watch(b.descriptionInput,text->vm.set("description",text));
        b.kindGroup.setOnCheckedChangeListener((group,id)->vm.set("kind",id==R.id.exam?1:0));
        b.priorityGroup.setOnCheckedChangeListener((group,id)->vm.set("priority",id==R.id.high?2:id==R.id.low?0:1));
        b.completed.setOnCheckedChangeListener((button,checked)->vm.set("completed",checked));
        b.subjectButton.setOnClickListener(v->{ Bundle args=new Bundle();args.putString("resultKey","task_subject"); NavHostFragment.findNavController(this).navigate(R.id.subjectPickerFragment,args); });
        b.manageSubjects.setOnClickListener(v->Ui.go(this,R.id.subjectListFragment,0));
        getParentFragmentManager().setFragmentResultListener("task_subject",getViewLifecycleOwner(),(key,result)->{
            vm.set("subject",result.getLong("subjectId")); updateSubject();
        });
        b.dueButton.setOnClickListener(v->pickDue());
        b.saveButton.setOnClickListener(v->{
            if(vm.text("title").trim().isEmpty()) { b.titleInput.setError(getString(R.string.core_required));return; }
            if(vm.number("subject",0)==0) { Toast.makeText(requireContext(),R.string.core_subject_required,Toast.LENGTH_SHORT).show();return; }
            vm.saveTask();
        });
        vm.revision.observe(getViewLifecycleOwner(),revision->render());
        vm.subjects.observe(getViewLifecycleOwner(),subjects->updateSubject());
        vm.busy.observe(getViewLifecycleOwner(),busy->{b.progress.setVisibility(busy?View.VISIBLE:View.GONE);enableForm();});
        vm.errors.observe(getViewLifecycleOwner(),event->{String error=event.take();if(error!=null)message(error);});
        vm.saved.observe(getViewLifecycleOwner(),event->{if(event.take()!=null){Toast.makeText(requireContext(),R.string.saved,Toast.LENGTH_SHORT).show();NavHostFragment.findNavController(this).navigateUp();}});
        vm.loadTask(getArguments()==null?0:getArguments().getLong("id"));
    }
    private void render() {
        b.titleInput.setText(vm.text("title"));b.descriptionInput.setText(vm.text("description"));
        b.kindGroup.check(vm.number("kind",0)==1?R.id.exam:R.id.assignment);
        int p=(int)vm.number("priority",1);b.priorityGroup.check(p==2?R.id.high:p==0?R.id.low:R.id.normal);
        b.completed.setChecked(vm.flag("completed"));
        b.dueButton.setText(Ui.dateTime(vm.number("due",System.currentTimeMillis()+86400000L)));
        enableForm();updateSubject();
    }
    private void enableForm() {
        boolean enabled=!vm.isBusy()&&vm.flag("initialized");
        b.saveButton.setEnabled(enabled); b.titleInput.setEnabled(enabled); b.descriptionInput.setEnabled(enabled);
        b.subjectButton.setEnabled(enabled); b.manageSubjects.setEnabled(enabled); b.dueButton.setEnabled(enabled);
        b.assignment.setEnabled(enabled); b.exam.setEnabled(enabled); b.low.setEnabled(enabled);
        b.normal.setEnabled(enabled); b.high.setEnabled(enabled); b.completed.setEnabled(enabled);
    }
    private void updateSubject() { if(b!=null)b.subjectButton.setText(vm.number("subject",0)==0?getString(R.string.select_subject):CoreText.subject(requireContext(),vm.subjects.getValue(),vm.number("subject",0))); }
    private void pickDue() {
        Calendar selected=Calendar.getInstance();selected.setTimeInMillis(vm.number("due",System.currentTimeMillis()+86400000L));
        new DatePickerDialog(requireContext(),(picker,year,month,day)->{
            selected.set(Calendar.YEAR,year);selected.set(Calendar.MONTH,month);selected.set(Calendar.DAY_OF_MONTH,day);
            if(!isAdded())return;
            new TimePickerDialog(requireContext(),(time,hour,minute)->{
                selected.set(Calendar.HOUR_OF_DAY,hour);selected.set(Calendar.MINUTE,minute);selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);
                vm.set("due",selected.getTimeInMillis());if(b!=null)b.dueButton.setText(Ui.dateTime(selected.getTimeInMillis()));
            },selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),true).show();
        },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show();
    }
    @Override public void onDestroyView(){b=null;super.onDestroyView();}
}
