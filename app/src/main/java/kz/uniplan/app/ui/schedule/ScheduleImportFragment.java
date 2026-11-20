package kz.uniplan.app.ui.schedule;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentScheduleImportBinding;
import kz.uniplan.app.databinding.ItemScheduleImportBinding;
import kz.uniplan.app.domain.schedule.ScheduleImport;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;
import kz.uniplan.app.ui.tasks.CoreText;
import kz.uniplan.app.util.Appearance;

public final class ScheduleImportFragment extends BaseFragment {
    private FragmentScheduleImportBinding b;
    private ScheduleImportViewModel vm;
    private final PreviewAdapter adapter=new PreviewAdapter();
    @Override public View onCreateView(@NonNull LayoutInflater inflater,ViewGroup parent,Bundle state){b=FragmentScheduleImportBinding.inflate(inflater,parent,false);return b.getRoot();}
    @Override public void onViewCreated(@NonNull View view,Bundle state){
        super.onViewCreated(view,state);vm=new ViewModelProvider(this).get(ScheduleImportViewModel.class);
        b.sourceInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ScheduleImport.MAX_INPUT)});
        b.sourceInput.setText(vm.source());Ui.watch(b.sourceInput,vm::edit);
        b.previewList.setLayoutManager(new LinearLayoutManager(requireContext()));b.previewList.setAdapter(adapter);
        b.pasteButton.setOnClickListener(v->paste());b.websiteButton.setOnClickListener(v->website());
        b.previewButton.setOnClickListener(v->{hideKeyboard();vm.analyze();});b.importButton.setOnClickListener(v->vm.save());
        vm.sourceChanges().observe(getViewLifecycleOwner(),text->{if(!b.sourceInput.getText().toString().equals(text))b.sourceInput.setText(text);});
        vm.preview.observe(getViewLifecycleOwner(),preview->render());vm.busy.observe(getViewLifecycleOwner(),busy->render());
        vm.errors.observe(getViewLifecycleOwner(),event->{String code=event.take();if(code!=null)Toast.makeText(requireContext(),error(code),Toast.LENGTH_LONG).show();});
        vm.saved.observe(getViewLifecycleOwner(),event->{ScheduleImport.Outcome result=event.take();if(result!=null){Toast.makeText(requireContext(),getString(R.string.import_done,result.added,result.duplicates,result.subjects),Toast.LENGTH_LONG).show();NavHostFragment.findNavController(this).navigateUp();}});
        render();
    }
    private void paste(){
        ClipboardManager clipboard=(ClipboardManager)requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip=clipboard.getPrimaryClip();
        if(clip==null||clip.getItemCount()==0){Toast.makeText(requireContext(),R.string.import_clipboard_empty,Toast.LENGTH_SHORT).show();return;}
        ClipData.Item item=clip.getItemAt(0);String html=item.getHtmlText();
        CharSequence plain=item.getText();String plainText=plain==null?"":plain.toString();
        boolean rich=html!=null&&html.toLowerCase(java.util.Locale.ROOT).contains("<table")&&html.length()<=ScheduleImport.MAX_INPUT;
        String text=rich?html:plainText;
        if(text.length()>ScheduleImport.MAX_INPUT||(!rich&&text.isEmpty()&&html!=null&&html.length()>ScheduleImport.MAX_INPUT)){
            Toast.makeText(requireContext(),R.string.import_limit,Toast.LENGTH_LONG).show();return;
        }
        b.sourceInput.setText(text);hideKeyboard();
        vm.analyze(rich&&plainText.length()<=ScheduleImport.MAX_INPUT?plainText:null);
    }
    private void website(){
        EditText input=new EditText(requireContext());input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://");input.setText(vm.url());input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        int pad=Math.round(24*getResources().getDisplayMetrics().density);input.setPadding(pad,pad,pad,pad);
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.import_website).setMessage(R.string.import_website_help).setView(input)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.import_load,(dialog,which)->{hideKeyboard();vm.download(input.getText().toString().trim());}).show();
    }
    private void hideKeyboard(){if(getView()!=null)((android.view.inputmethod.InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getView().getWindowToken(),0);}
    private void render(){
        if(b==null)return;boolean busy=Boolean.TRUE.equals(vm.busy.getValue());ScheduleImport.Preview preview=vm.preview.getValue();
        b.progress.setVisibility(busy?View.VISIBLE:View.GONE);b.sourceInput.setEnabled(!busy);b.pasteButton.setEnabled(!busy);b.websiteButton.setEnabled(!busy);b.previewButton.setEnabled(!busy);
        b.importButton.setEnabled(!busy&&preview!=null&&preview.canImport());adapter.submit(preview==null?new ArrayList<>():preview.entries);
        b.summary.setText(preview==null?getString(R.string.import_preview_hint):getString(R.string.import_count,preview.entries.size()));
        StringBuilder issues=new StringBuilder();
        if(preview!=null){
            for(int i=0;i<Math.min(4,preview.issues.size());i++){ScheduleImport.Issue issue=preview.issues.get(i);if(issues.length()>0)issues.append('\n');issues.append(issue.row==0?getString(error(issue.code)):getString(R.string.import_row_error,issue.row,getString(error(issue.code))));}
            if(preview.issues.size()>4)issues.append('\n').append(getString(R.string.import_more_errors,preview.issues.size()-4));
            if(preview.ignoredAccessFields>0){if(issues.length()>0)issues.append('\n');issues.append(getString(R.string.import_access_omitted));}
            if(preview.ignoredLinkFields>0){if(issues.length()>0)issues.append('\n');issues.append(getString(R.string.import_link_note_omitted));}
        }
        b.issues.setText(issues.toString());b.issues.setVisibility(issues.length()==0?View.GONE:View.VISIBLE);
    }
    private int error(String code){
        switch(code){case "empty":return R.string.import_clipboard_empty;case "limit":return R.string.import_limit;case "header":case "format":return R.string.import_format;
            case "day":return R.string.import_day_error;case "time":return R.string.import_time_error;case "number":return R.string.lesson_invalid_number;
            case "fields":case "validation":return R.string.import_fields_error;case "url":return R.string.import_url_error;case "login":return R.string.import_login;
            case "save":return R.string.import_save_failed;case "session":return R.string.error_session;default:return R.string.import_network;}
    }
    private static final class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.Holder> {
        private List<ScheduleImport.Entry> entries=new ArrayList<>();
        void submit(List<ScheduleImport.Entry> value){entries=value;notifyDataSetChanged();}
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(ItemScheduleImportBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
        @Override public void onBindViewHolder(@NonNull Holder h,int position){ScheduleImport.Entry e=entries.get(position);Context c=h.itemView.getContext();
            h.b.title.setText(e.subject);h.b.time.setText(c.getResources().getStringArray(R.array.core_days)[e.day-1]+" · "+CoreText.time(c,e.start)+"–"+CoreText.time(c,e.end));
            String details=e.type+(e.room.isEmpty()?"":" · "+e.room)+(e.teacher.isEmpty()?"":"\n"+e.teacher)+(e.url.isEmpty()?"":"\n"+e.url);
            h.b.details.setText(details);Appearance.applyCard(h.b.getRoot(),position%2==0?"mint":"lavender");}
        @Override public int getItemCount(){return entries.size();}
        static final class Holder extends RecyclerView.ViewHolder{final ItemScheduleImportBinding b;Holder(ItemScheduleImportBinding b){super(b.getRoot());this.b=b;}}
    }
    @Override public void onDestroyView(){b.previewList.setAdapter(null);b=null;super.onDestroyView();}
}
