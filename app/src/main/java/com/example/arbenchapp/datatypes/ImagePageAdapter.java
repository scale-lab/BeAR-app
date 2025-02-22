package com.example.arbenchapp.datatypes;

import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.arbenchapp.R;

import java.util.List;

public class ImagePageAdapter extends RecyclerView.Adapter<ImagePageAdapter.ImageViewHolder> {
    private final List<ImagePage> pages;

    public ImagePageAdapter(List<ImagePage> pages) {
        this.pages = pages;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int orientation = parent.getResources().getConfiguration().orientation;
        String orientationString = orientation == Configuration.ORIENTATION_LANDSCAPE ? "LANDSCAPE" : "PORTRAIT";
        System.out.println("ONNX orientation: " + orientationString);
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.page_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        ImagePage page = pages.get(position);
        holder.imageView.setImageBitmap(page.getImage());
        holder.captionView.setText(page.getCaption());
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView captionView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            captionView = itemView.findViewById(R.id.captionView);
        }
    }
}
