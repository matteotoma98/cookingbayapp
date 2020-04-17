package it.tpt.cookingbayapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import it.tpt.cookingbayapp.recipeObject.Recipe;


public class ViewRecipeActivity extends AppCompatActivity {

    private ViewPager mViewPager;
    private TabLayout mTabLayout;

    private VrFragment mVrFragment;
    private ComFragment mComFragment;

    boolean iconset;
    boolean favouriteset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_recipe);

        mViewPager = findViewById(R.id.view_pager);
        mTabLayout = findViewById(R.id.tab_layout);

        mVrFragment = new VrFragment();
        mComFragment = new ComFragment();
        iconset = false;
        favouriteset = true;

        //Ottieni la ricetta passata dallo startActivity
        Intent intent = getIntent();
        Recipe recipe = (Recipe) intent.getSerializableExtra("recipe");
        String recipeId = intent.getStringExtra("recipeId");
        Bundle recipeBundle = new Bundle();
        recipeBundle.putSerializable("recipe", recipe);
        recipeBundle.putString("recipeId", recipeId);
        mVrFragment.setArguments(recipeBundle); //Passa la ricetta al fragment della visualizzazione della ricetta

        Bundle commentBundle = new Bundle();
        commentBundle.putSerializable("comments", recipe.getComments());
        commentBundle.putString("recipeId", recipeId);
        mComFragment.setArguments(commentBundle); //Passa i commenti al fragment della visualizzazione dei commenti

        getSupportActionBar().setTitle(recipe.getTitle());

        mTabLayout.setupWithViewPager(mViewPager);

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(getSupportFragmentManager(), 0);
        viewPagerAdapter.addFragment(mVrFragment, "Ricetta");
        viewPagerAdapter.addFragment(mComFragment, "Commenti");
        mViewPager.setAdapter(viewPagerAdapter);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.vr_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.addFavourite) {
            if (favouriteset) {
                if (iconset) {
                    item.setIcon(R.drawable.ic_favorite_border_black_24dp);
                    iconset = false;
                    favouriteset = true;
                } else {
                    item.setIcon(R.drawable.ic_favorite_black_24dp);
                    iconset = true;
                    favouriteset = false;
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private class ViewPagerAdapter extends FragmentPagerAdapter {

        private List<Fragment> fragments = new ArrayList<>();
        private List<String> fragmentTitle = new ArrayList<>();

        public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        public void addFragment(Fragment fragment, String title) {
            fragments.add(fragment);
            fragmentTitle.add(title);

        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitle.get(position);
        }
    }
}
