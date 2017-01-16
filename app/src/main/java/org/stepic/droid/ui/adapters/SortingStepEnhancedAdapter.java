package org.stepic.droid.ui.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.h6ah4i.android.widget.advrecyclerview.draggable.DraggableItemAdapter;
import com.h6ah4i.android.widget.advrecyclerview.draggable.ItemDraggableRange;
import com.h6ah4i.android.widget.advrecyclerview.utils.AbstractDraggableItemViewHolder;

import org.stepic.droid.R;
import org.stepic.droid.model.Option;
import org.stepic.droid.ui.custom.LatexSupportableEnhancedFrameLayout;
import org.stepic.droid.ui.util.ViewUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SortingStepEnhancedAdapter extends RecyclerView.Adapter<SortingStepEnhancedAdapter.OptionViewHolder>
        implements DraggableItemAdapter<SortingStepEnhancedAdapter.OptionViewHolder> {

    private final List<Option> data;

    public SortingStepEnhancedAdapter(List<Option> data) {
        super();
        this.data = data;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).getPositionId();
    }

    @Override
    public OptionViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.view_sorting_option, parent, false);
        return new OptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(OptionViewHolder holder, int position) {
        String text = data.get(position).getValue();
        holder.enhancedText.setPlainOrLaTeXText(text);

//        final int dragState = holder.getDragStateFlags();
//        if ((dragState & DraggableItemConstants.STATE_FLAG_IS_UPDATED) != 0) {
//            @DrawableRes
//            int backgroundResource;
//
//            if ((dragState & DraggableItemConstants.STATE_FLAG_IS_ACTIVE) != 0) {
//                backgroundResource = R.drawable.bg_item_dragging_active_state;
//                // need to clear drawable state here to get correct appearance of the dragging item.
//                DrawableUtils.clearState(holder.container.getForeground());
//            } else if ((dragState & DraggableItemConstants.STATE_FLAG_DRAGGING) != 0) {
//                backgroundResource = R.drawable.bg_item_dragging_state;
//            } else {
//                backgroundResource = R.drawable.bg_item_normal_state;
//            }
//
//            holder.container.setBackgroundResource(backgroundResource);
//        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public boolean onCheckCanStartDrag(OptionViewHolder holder, int position, int x, int y) {
        return ViewUtils.hitTest(holder.sortController, x, y);
    }

    @Override
    public ItemDraggableRange onGetItemDraggableRange(OptionViewHolder holder, int position) {
        // no drag-sortable range specified
        return null;
    }

    @Override
    public void onMoveItem(int fromPosition, int toPosition) {
        if (fromPosition == toPosition) {
            return;
        }
        data.add(toPosition, data.remove(fromPosition));
        notifyItemMoved(fromPosition, toPosition);
    }

    @Override
    public boolean onCheckCanDrop(int draggingPosition, int dropPosition) {
        return true;
    }

    public static class OptionViewHolder extends AbstractDraggableItemViewHolder {

        @BindView(R.id.container)
        View container;

        @BindView(R.id.sort_icon)
        View sortController;

        @BindView(R.id.option_text)
        LatexSupportableEnhancedFrameLayout enhancedText;


        public OptionViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

    }

}
