package kz.uniplan.app.ui.notes;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import kz.uniplan.app.R;
import kz.uniplan.app.data.local.entity.NoteEntity;
import kz.uniplan.app.databinding.RowNoteBinding;
import kz.uniplan.app.ui.common.Ui;

final class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteHolder> {
    interface OpenListener { void open(long id); }
    private final List<NoteEntity> items = new ArrayList<>();
    private final OpenListener listener;

    NoteAdapter(OpenListener listener) { this.listener = listener; setHasStableIds(true); }
    void submit(List<NoteEntity> values) { items.clear(); items.addAll(values); notifyDataSetChanged(); }
    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new NoteHolder(RowNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        NoteEntity note = items.get(position);
        holder.binding.noteTitle.setText(note.title);
        holder.binding.noteBody.setText(note.content);
        holder.binding.noteModified.setText(holder.itemView.getContext().getString(R.string.notes_modified, Ui.dateTime(note.updatedAt)));
        holder.binding.getRoot().setContentDescription(holder.itemView.getContext().getString(R.string.notes_open, note.title));
        holder.binding.getRoot().setOnClickListener(v -> listener.open(note.id));
        kz.uniplan.app.util.Appearance.applyCard(holder.binding.getRoot());
    }
    static final class NoteHolder extends RecyclerView.ViewHolder {
        final RowNoteBinding binding;
        NoteHolder(RowNoteBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
