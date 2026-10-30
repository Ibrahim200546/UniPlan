package kz.uniplan.app.ui.notes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import kz.uniplan.app.databinding.FragmentNoteEditorBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public final class NoteEditorFragment extends BaseFragment {
    private FragmentNoteEditorBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        binding = FragmentNoteEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        NoteEditViewModel vm = new ViewModelProvider(requireParentFragment()).get(NoteEditViewModel.class);
        vm.content.observe(getViewLifecycleOwner(), text -> {
            String current = binding.noteContent.getText() == null ? "" : binding.noteContent.getText().toString();
            if (!current.equals(text)) {
                int cursor = Math.max(0, binding.noteContent.getSelectionStart());
                binding.noteContent.setText(text);
                binding.noteContent.setSelection(Math.min(cursor, text.length()));
            }
        });
        Ui.watch(binding.noteContent, vm::setContent);
        vm.available.observe(getViewLifecycleOwner(), available -> binding.noteContent.setEnabled(Boolean.TRUE.equals(available) && !Boolean.TRUE.equals(vm.busy.getValue())));
        vm.busy.observe(getViewLifecycleOwner(), busy -> binding.noteContent.setEnabled(!Boolean.TRUE.equals(busy) && Boolean.TRUE.equals(vm.available.getValue())));
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
