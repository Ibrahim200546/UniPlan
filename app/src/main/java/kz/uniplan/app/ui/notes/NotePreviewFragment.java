package kz.uniplan.app.ui.notes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentNotePreviewBinding;
import kz.uniplan.app.ui.common.BaseFragment;

public final class NotePreviewFragment extends BaseFragment {
    private FragmentNotePreviewBinding binding;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        binding = FragmentNotePreviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        NoteEditViewModel vm = new ViewModelProvider(requireParentFragment()).get(NoteEditViewModel.class);
        vm.title.observe(getViewLifecycleOwner(), title -> binding.previewTitle.setText(title.trim().isEmpty() ? getString(R.string.notes_untitled) : title));
        vm.content.observe(getViewLifecycleOwner(), text -> binding.previewBody.setText(text));
    }
    @Override public void onDestroyView() { binding = null; super.onDestroyView(); }
}
