package io.finett.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FeatureAdapter extends RecyclerView.Adapter<FeatureAdapter.ViewHolder> {
    
    private List<Feature> features;
    
    public FeatureAdapter(List<Feature> features) {
        this.features = features;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feature, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Feature feature = features.get(position);
        holder.titleTextView.setText(feature.getTitle());
        holder.descriptionTextView.setText(feature.getDescription());
        holder.iconImageView.setImageResource(feature.getIconResourceId());
    }
    
    @Override
    public int getItemCount() {
        return features.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView descriptionTextView;
        ImageView iconImageView;
        
        ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.featureTitle);
            descriptionTextView = itemView.findViewById(R.id.featureDescription);
            iconImageView = itemView.findViewById(R.id.featureIcon);
        }
    }
} 