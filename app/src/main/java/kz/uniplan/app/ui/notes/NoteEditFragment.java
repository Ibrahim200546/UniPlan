package kz.uniplan.app.ui.notes;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.SubjectEntity;
import kz.uniplan.app.databinding.FragmentNoteEditBinding;
import kz.uniplan.app.ui.common.BaseFragment;
import kz.uniplan.app.ui.common.Ui;

public final class NoteEditFragment extends BaseFragment {
    private static final String SUBJECT_RESULT = "note_subject_pick";
    private FragmentNoteEditBinding binding;
    private NoteEditViewModel vm;
    private FragmentManager.OnBackStackChangedListener childBackListener;
    private boolean exitDialogShowing;
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), uri -> { if (vm != null) vm.exportTo(uri); });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle saved) {
        binding = FragmentNoteEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        vm = new ViewModelProvider(this).get(NoteEditViewModel.class);
        vm.title.observe(getViewLifecycleOwner(), text -> {
            String current = binding.noteTitle.getText() == null ? "" : binding.noteTitle.getText().toString();
            if (!current.equals(text)) binding.noteTitle.setText(text);
            binding.noteTitleLayout.setError(null);
        });
        Ui.watch(binding.noteTitle, vm::setTitle);
        vm.dirty.observe(getViewLifecycleOwner(), dirty -> binding.noteStatus.setText(Boolean.TRUE.equals(dirty) || vm.id() == 0 ? R.string.notes_unsaved : R.string.notes_saved_state));
        vm.updatedAt.observe(getViewLifecycleOwner(), updated -> {
            binding.noteTimestamps.setText(updated == null || updated == 0L ? "" : getString(R.string.notes_dates, Ui.dateTime(vm.createdAt()), Ui.dateTime(updated)));
            binding.noteDelete.setVisibility(vm.id() > 0 ? View.VISIBLE : View.GONE);
        });
        vm.subjectId.observe(getViewLifecycleOwner(), ignored -> showSubject());
        vm.subjects.observe(getViewLifecycleOwner(), ignored -> showSubject());
        vm.available.observe(getViewLifecycleOwner(), ignored -> updateEnabled());
        vm.busy.observe(getViewLifecycleOwner(), ignored -> updateEnabled());
        vm.events.observe(getViewLifecycleOwner(), event -> {
            if (!event.consume()) return;
            switch (event.action) {
                case "saved": show(R.string.saved); binding.noteDelete.setVisibility(View.VISIBLE); break;
                case "deleted": NavHostFragment.findNavController(this).navigateUp(); break;
                case "copied": show(R.string.notes_copy_saved); break;
                case "opened": show(R.string.notes_copy_opened); popPreview(); break;
                case "save_first": show(R.string.notes_save_first); break;
                case "exported": show(R.string.notes_exported); break;
                case "cancelled": Toast.makeText(requireContext(), R.string.notes_export_cancelled, Toast.LENGTH_SHORT).show(); break;
                default:
                    if ("validation".equals(event.error)) binding.noteTitleLayout.setError(getString(R.string.error_validation));
                    message(event.error);
            }
        });
        binding.noteSave.setOnClickListener(v -> vm.save());
        binding.noteDelete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete).setMessage(vm.titleText())
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete, (dialog, which) -> vm.delete()).show());
        binding.noteSubject.setOnClickListener(v -> {
            Bundle args = new Bundle(); args.putString("resultKey", SUBJECT_RESULT); args.putBoolean("allowNone", true);
            NavHostFragment.findNavController(this).navigate(R.id.subjectPickerFragment, args);
        });
        binding.noteManageSubjects.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.subjectListFragment));
        getParentFragmentManager().setFragmentResultListener(SUBJECT_RESULT, getViewLifecycleOwner(), (key, result) -> vm.setSubject(result.getLong("subjectId", 0L)));
        binding.notePreview.setOnClickListener(v -> togglePreview());
        binding.noteCopies.setOnClickListener(v -> copies());
        FragmentManager fm = getChildFragmentManager();
        if (fm.findFragmentById(R.id.noteChildContainer) == null) {
            fm.beginTransaction().setReorderingAllowed(true).replace(R.id.noteChildContainer, new NoteEditorFragment(), "editor").commitNow();
        }
        childBackListener = this::showPreviewLabel;
        fm.addOnBackStackChangedListener(childBackListener);
        showPreviewLabel();
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleUp(); }
        });
    }
    private void showSubject() {
        if (binding == null) return;
        Long selected = vm.subjectId.getValue();
        binding.noteSubject.setText(R.string.no_subject);
        if (selected == null || selected == 0 || vm.subjects.getValue() == null) return;
        for (SubjectEntity subject : vm.subjects.getValue()) if (subject.id == selected) { binding.noteSubject.setText(subject.name); break; }
    }
    private void updateEnabled() {
        boolean busy = Boolean.TRUE.equals(vm.busy.getValue());
        boolean enabled = Boolean.TRUE.equals(vm.available.getValue()) && !busy;
        binding.noteBusy.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.noteSave.setEnabled(enabled); binding.noteDelete.setEnabled(enabled);
        binding.noteTitle.setEnabled(enabled); binding.noteSubject.setEnabled(enabled);
        binding.noteManageSubjects.setEnabled(enabled); binding.noteCopies.setEnabled(enabled); binding.notePreview.setEnabled(enabled);
    }
    private void show(int res) { Snackbar.make(binding.getRoot(), res, Snackbar.LENGTH_LONG).show(); }
    private void showPreviewLabel() {
        if (binding != null) binding.notePreview.setText(getChildFragmentManager().getBackStackEntryCount() == 0 ? R.string.preview : R.string.edit);
    }
    private void togglePreview() {
        if (popPreview()) return;
        FragmentManager fm = getChildFragmentManager();
        if (fm.isStateSaved()) return;
        fm.beginTransaction().setReorderingAllowed(true).replace(R.id.noteChildContainer, new NotePreviewFragment(), "preview")
                .addToBackStack("note-preview").commit();
        fm.executePendingTransactions();
    }
    public boolean popPreview() {
        FragmentManager fm = getChildFragmentManager();
        if (fm.getBackStackEntryCount() > 0 && !fm.isStateSaved()) { fm.popBackStackImmediate(); return true; }
        return false;
    }
    /** Called by both system Back and MainActivity's toolbar Up. */
    public void handleUp() {
        if (popPreview() || binding == null || exitDialogShowing) return;
        if (Boolean.TRUE.equals(vm.busy.getValue())) { show(R.string.loading); return; }
        if (vm.isDirty()) {
            exitDialogShowing = true;
            new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.notes_discard_title).setMessage(R.string.notes_discard_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.notes_discard, (dialog, which) -> NavHostFragment.findNavController(this).navigateUp())
                    .setOnDismissListener(dialog -> exitDialogShowing = false).show();
        } else NavHostFragment.findNavController(this).navigateUp();
    }
    private void copies() {
        PopupMenu menu = new PopupMenu(requireContext(), binding.noteCopies);
        menu.getMenu().add(0, 1, 0, R.string.notes_internal_save);
        menu.getMenu().add(0, 2, 1, R.string.notes_internal_open);
        menu.getMenu().add(0, 3, 2, R.string.notes_external_save);
        menu.getMenu().add(0, 4, 3, R.string.notes_external_open);
        menu.getMenu().add(0, 5, 4, R.string.notes_export);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: vm.saveCopy(false); break;
                case 2: confirmRead(false); break;
                case 3: vm.saveCopy(true); break;
                case 4: confirmRead(true); break;
                case 5:
                    if (vm.prepareExport()) exportLauncher.launch("UniPlan-note-" + vm.id() + ".txt");
                    break;
                default: return false;
            }
            return true;
        });
        menu.show();
    }
    private void confirmRead(boolean external) {
        new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.notes_copy_confirm)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok, (dialog, which) -> vm.readCopy(external)).show();
    }
    @Override public void onDestroyView() {
        if (childBackListener != null) getChildFragmentManager().removeOnBackStackChangedListener(childBackListener);
        childBackListener = null; binding = null; super.onDestroyView();
    }
}
