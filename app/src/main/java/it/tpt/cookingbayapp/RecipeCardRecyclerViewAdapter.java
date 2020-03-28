package it.tpt.cookingbayapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import it.tpt.cookingbayapp.recipeObject.Recipe;

public class RecipeCardRecyclerViewAdapter extends RecyclerView.Adapter<RecipeCardViewHolder> {

    private List<Recipe> recipeList;
    Context mContext;

    RecipeCardRecyclerViewAdapter(Context c, List<Recipe> recipeList) {
        mContext = c;
        this.recipeList = recipeList;
    }

    @NonNull
    @Override
    public RecipeCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View layoutView = LayoutInflater.from(parent.getContext()).inflate(R.layout.recipe_card, parent, false);
        return new RecipeCardViewHolder(layoutView);
    }

    //Assegna le informazioni della ricetta dinamicamente alla card
    @Override
    public void onBindViewHolder(@NonNull RecipeCardViewHolder holder, int position) {
        if (recipeList != null && position < recipeList.size()) {
            final Recipe recipe = recipeList.get(position);
            holder.title.setText(recipe.getTitle());
            holder.user.setText(recipe.getAuthor());
            //imageRequester.setImageFromUrl(holder.productImage, product.url);
            holder.preview.setImageResource(R.drawable.maxresdefault);

            //Rendere cliccabile la card e passare le informazioni all'activity ViewRecipeActivity
            holder.setRecipeClickListener(new RecipeClickListener() {
                @Override
                public void onRecipeClickListener(View v, int position) {
                    Intent intent = new Intent(mContext, ViewRecipeActivity.class);
                    intent.putExtra("recipeTitle", recipe.getTitle());
                    intent.putExtra("recipeAuthor", recipe.getAuthor());
                    intent.putExtra("sectionList", recipe.getSections());
                    mContext.startActivity(intent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return recipeList.size();
    }
}
