package com.example.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import java.util.Calendar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.example.freshtrack.adapters.FoodItemAdapter;
import com.example.freshtrack.models.FoodItem;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.example.freshtrack.utils.SwipeToDeleteCallback;

public class MainActivityHome extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseModel firebaseModel;
    private RecyclerView recyclerView;
    public FoodItemAdapter adapter;
    private List<FoodItem> foodItems;
    private EditText searchBox;
    private static final String PREFS_NAME = "LayoutPrefs";
    private static final String KEY_LAYOUT = "layout_type";
    private String currentFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_home);

        // Get filter from intent
        currentFilter = getIntent().getStringExtra("FILTER");
        if (currentFilter == null) currentFilter = "all";

        // Set up toolbar with appropriate title
        String title;
        switch (currentFilter) {
            case "fresh":
                title = "Fresh Items";
                break;
            case "expiring_soon":
                title = "Expiring Soon";
                break;
            case "expired":
                title = "Expired Items";
                break;
            default:
                title = "All Items";
                break;
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }

        // Initialize Firebase components
        mAuth = FirebaseAuth.getInstance();
        firebaseModel = new FirebaseModel();

        // Initialize RecyclerView
        initializeRecyclerView();

        // Initialize search functionality
        searchBox = findViewById(R.id.searchBox);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set up bottom navigation
        setupBottomNavigation();

        // Load food items
        loadFoodItems();
    }

    private void setupBottomNavigation() {
        View bottomNav = findViewById(R.id.bottomNav);
        View btnHome = bottomNav.findViewById(R.id.btnHome);
        View btnDashboard = bottomNav.findViewById(R.id.btnDashboard);
        View btnAdd = bottomNav.findViewById(R.id.btnAdd);
        View btnNotifications = bottomNav.findViewById(R.id.btnNotifications);
        View btnSettings = bottomNav.findViewById(R.id.btnSettings);

        btnHome.setOnClickListener(v -> {
            // Already on Home, do nothing
        });

        btnDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivityHome.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivityHome.this, AddListActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivityHome.this, NotificationsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivityHome.this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void loadFoodItems() {
        String userId = mAuth.getCurrentUser().getUid();
        firebaseModel.getFoodItemsByUser(userId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                foodItems.clear();
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    FoodItem item = snapshot.getValue(FoodItem.class);
                    if (item != null) {
                        Calendar expiryCalendar = Calendar.getInstance();
                        expiryCalendar.setTimeInMillis(item.getExpiryDate());
                        expiryCalendar.set(Calendar.HOUR_OF_DAY, 0);
                        expiryCalendar.set(Calendar.MINUTE, 0);
                        expiryCalendar.set(Calendar.SECOND, 0);
                        expiryCalendar.set(Calendar.MILLISECOND, 0);

                        long daysUntilExpiry = (expiryCalendar.getTimeInMillis() - today.getTimeInMillis()) 
                            / (24 * 60 * 60 * 1000);

                        boolean shouldAdd = false;
                        switch (currentFilter) {
                            case "fresh":
                                shouldAdd = daysUntilExpiry > 2;
                                break;
                            case "expiring_soon":
                                shouldAdd = daysUntilExpiry >= 0 && daysUntilExpiry <= 2;
                                break;
                            case "expired":
                                shouldAdd = daysUntilExpiry < 0;
                                break;
                            default: // "all"
                                shouldAdd = true;
                                break;
                        }

                        if (shouldAdd) {
                            foodItems.add(item);
                        }
                    }
                }

                adapter.updateItems(foodItems);
                
                // Show message if no items found
                TextView noItemsText = findViewById(R.id.noItemsText);
                if (noItemsText != null) {
                    if (foodItems.isEmpty()) {
                        noItemsText.setVisibility(View.VISIBLE);
                        noItemsText.setText("No " + currentFilter.replace("_", " ") + " items found");
                    } else {
                        noItemsText.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("MainActivityHome", "Error loading food items", error.toException());
            }
        });
    }

    private void setLayoutManager() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String layoutType = prefs.getString(KEY_LAYOUT, "list");
        
        if (layoutType.equals("grid")) {
            // Use GridLayoutManager with 2 columns
            GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
            recyclerView.setLayoutManager(layoutManager);
        } else {
            // Use LinearLayoutManager for list view
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setReverseLayout(false);
            layoutManager.setStackFromEnd(false);
            recyclerView.setLayoutManager(layoutManager);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if layout preference has changed
        setLayoutManager();
    }

    private void initializeRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView);
        foodItems = new ArrayList<>();
        adapter = new FoodItemAdapter(foodItems);
        
        // Set layout based on user preference
        setLayoutManager();
        recyclerView.setAdapter(adapter);
        
        // Add swipe to delete functionality
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(
            new SwipeToDeleteCallback(adapter, this)
        );
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }
}