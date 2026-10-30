package kz.uniplan.app.ui.tasks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.snackbar.Snackbar;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.TaskEntity;
import kz.uniplan.app.databinding.FragmentTaskDetailBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public class TaskDetailFragment extends BaseFragment {
    private FragmentTaskDetailBinding b;
    private StudyViewModel vm;
    private long id;
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup container,Bundle state){b=FragmentTaskDetailBinding.inflate(inflater,container,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){ super.onViewCreated(view,state);
        vm=new ViewModelProvider(this).get(StudyViewModel.class);id=requireArguments().getLong("id");
        b.editButton.setOnClickListener(v->Ui.go(this,R.id.taskEditFragment,id));
        b.deleteButton.setOnClickListener(v->vm.deleteTask(id));
        b.completed.setOnClickListener(v->{TaskEntity t=vm.detail.getValue();if(t!=null){boolean value=b.completed.isChecked();b.completed.setChecked(t.completed);vm.complete(id,value);}});
        vm.detail.observe(getViewLifecycleOwner(),this::render);
        vm.subjects.observe(getViewLifecycleOwner(),subjects->render(vm.detail.getValue()));
        vm.tasks.observe(getViewLifecycleOwner(),tasks->{if(tasks!=null){for(TaskEntity t:tasks)if(t.id==id){vm.detail.setValue(t);return;} if(!vm.isBusy())render(null);}});
        vm.busy.observe(getViewLifecycleOwner(),busy->b.progress.setVisibility(busy?View.VISIBLE:View.GONE));
        vm.errors.observe(getViewLifecycleOwner(),event->{String error=event.take();if(error!=null)message(error);});
        vm.deletedTask.observe(getViewLifecycleOwner(),event->{
            TaskEntity t=event.take();if(t==null)return;
            View anchor=requireActivity().findViewById(android.R.id.content);
            Snackbar.make(anchor,R.string.deleted,Snackbar.LENGTH_LONG).setAction(R.string.undo,v->vm.restore(t)).show();
            NavHostFragment.findNavController(this).navigateUp();
        });
        vm.loadDetail(id);
    }
    private void render(TaskEntity t){
        if(b==null)return;
        b.editButton.setEnabled(t!=null);b.deleteButton.setEnabled(t!=null);b.completed.setEnabled(t!=null);
        if(t==null){b.titleText.setText(R.string.error_missing);b.subjectText.setText("");b.metaText.setText("");b.descriptionText.setText("");return;}
        b.titleText.setText(t.title);b.subjectText.setText(CoreText.subject(requireContext(),vm.subjects.getValue(),t.subjectId));
        b.metaText.setText(CoreText.taskMeta(requireContext(),t,vm.subjects.getValue()));
        b.descriptionText.setText(t.description==null||t.description.isEmpty()?getString(R.string.core_no_description):t.description);
        b.completed.setChecked(t.completed);
    }
    @Override public void onDestroyView(){b=null;super.onDestroyView();}
}
