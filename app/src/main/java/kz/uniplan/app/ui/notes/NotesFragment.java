package kz.uniplan.app.ui.notes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import kz.uniplan.app.R;
import kz.uniplan.app.databinding.FragmentNotesBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public final class NotesFragment extends BaseFragment {
    private FragmentNotesBinding binding;
    private NotesViewModel vm;
    private NoteAdapter adapter;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        vm = new ViewModelProvider(this).get(NotesViewModel.class);
        adapter = new NoteAdapter(id -> Ui.go(this, R.id.noteEditFragment, id));
        binding.notesList.setAdapter(adapter);
        layout();
        binding.noteSearch.setText(vm.query());
        Ui.watch(binding.noteSearch, vm::setQuery);
        binding.addNote.setOnClickListener(v -> Ui.go(this, R.id.noteEditFragment, 0));
        binding.toggleGrid.setOnClickListener(v -> { vm.toggleGrid(); layout(); });
        vm.notes.observe(getViewLifecycleOwner(), list -> {
            binding.notesLoading.setVisibility(View.GONE);
            adapter.submit(list);
            binding.notesEmpty.setText(vm.query().trim().isEmpty() ? R.string.notes_empty : R.string.notes_no_matches);
            binding.notesEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
    private void layout() {
        // One column for larger fonts keeps cards legible and the full row accessible.
        boolean useGrid = vm.grid() && getResources().getConfiguration().fontScale < 1.4f;
        binding.notesList.setLayoutManager(useGrid ? new GridLayoutManager(requireContext(), 2) : new LinearLayoutManager(requireContext()));
        binding.toggleGrid.setText(vm.grid() ? R.string.notes_list : R.string.notes_grid);
    }
    @Override public void onDestroyView() {
        binding.notesList.setAdapter(null);
        adapter = null;
        binding = null;
        super.onDestroyView();
    }
}
