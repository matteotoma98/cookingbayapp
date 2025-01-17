package it.tpt.cookingbayapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.tpt.cookingbayapp.ingredientsRecycler.IngredientsRecyclerViewAdapter;
import it.tpt.cookingbayapp.recipeObject.Comment;
import it.tpt.cookingbayapp.recipeObject.Ingredient;
import it.tpt.cookingbayapp.recipeObject.Recipe;
import it.tpt.cookingbayapp.recipeObject.Section;
import it.tpt.cookingbayapp.stepRecycler.Step;
import it.tpt.cookingbayapp.stepRecycler.StepAdapter;

/**
 * Activity utilizzata per creare o modificare le ricette esistenti
 * Se si sta modificando la ricetta non si può aggiungere ed eliminare step
 * per limitazioni dovute a Firebase
 */
public class CreateRecipe extends AppCompatActivity {

    private ImageView imgPreview;
    private TextInputEditText title, totalTime, ingName, ingQuantity;
    private TextInputLayout ddType;
    private AutoCompleteTextView actwType;
    private Button btnAddStep, btnAddIng, btnDelIng;
    boolean isUploading; //Utilizzato per evitare che l'utente clicchi nuovamente il pulsante SALVA (Che effettua l'upload)
    boolean isEditing; //Controlla se l'activity è stata avviata da modifica piuttosto che crea ricetta
    private String folder;
    private String recipeId; //Id della ricetta esistente (in modifica)
    private String generatedId; //Id generato in modo random all'atto della creazione di una nuova ricetta

    //Questo oggetto step serve per comodità, solo i metodi setUrl() e getUrl() vengono utilizzati all'interno di ImagePickActivity
    private Step main;

    //RecyclerView utilizzate rispettivamente per la visualizzazione degli ingredienti e degli step
    private RecyclerView iRecyclerView;
    private IngredientsRecyclerViewAdapter iAdapter;
    private RecyclerView mRecyclerView;
    private StepAdapter mAdapter;


    private final static int PREVIEW_REQUEST = 234;
    private final static int STEP_REQUEST = 236;

    private Recipe mRecipe; //Ricetta che verrà caricata sul FireStore

    //Uri utilizzati per caricare sul FirebaseStorage l'immagine di anteprima e del primo step
    private Uri previewUri;
    private CheckUploadTask mCheckUploadTask; //AsyncTask utilizzato per tenere traccia del completamento di tutti gli upload

    private FirebaseUser currentUser;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_recipe);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //Istruzioni per settare correttamente l'AutoCompleteTextView (Menu a tendina)
        ddType = findViewById(R.id.dropdownType);
        actwType = findViewById(R.id.actw);
        String[] ddItems = new String[]{
                "Primo",
                "Secondo",
                "Dessert",
                "Antipasto",
                "Contorno",
                "Bevanda",
                "Panino"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(CreateRecipe.this, R.layout.dropdown_item, ddItems);
        actwType.setAdapter(adapter);

        //Assegnazioni degli oggetti Java ai corrispettivi elementi XML
        title = findViewById(R.id.createRecipeTitle);
        totalTime = findViewById(R.id.recipeTime);
        imgPreview = findViewById(R.id.imgAnteprima);
        ingName = findViewById(R.id.txtIngredient);
        ingQuantity = findViewById(R.id.txtQuantity);
        btnAddStep = findViewById(R.id.addstep);
        btnAddIng = findViewById(R.id.addingredient);
        btnDelIng = findViewById(R.id.deleteIngredient);

        isUploading = false; //Ovviamente falso all'atto di creazione dell'activity
        isEditing = getIntent().getBooleanExtra("edit", false); //Per controllare se è in corso una modifica o una nuova ricetta
        main = new Step("", previewUri);

        //Inizializzo Firebase
        db = FirebaseFirestore.getInstance();
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        //Inizializzo i RecyclerView
        mRecyclerView = findViewById(R.id.step_recycler);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        iRecyclerView = findViewById(R.id.ingDisplay_recycler);
        iRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        Intent intent = getIntent();
        if (isEditing) { //Se è in corso una modifica
            getSupportActionBar().setTitle("Modifica ricetta");
            recipeId = getIntent().getStringExtra("recipeId");
            btnAddStep.setVisibility(View.GONE); //Nascondi il bottone aggiungi step

            Recipe editRecipe = (Recipe) intent.getSerializableExtra("recipeToEdit"); //Ottieni la ricetta passata tramite lo StartActivityForResult da PersonalCardAdapter
            main.setUrl(editRecipe.getPreviewUrl());
            title.setText(editRecipe.getTitle());
            totalTime.setText(String.valueOf(editRecipe.getTime()));
            actwType.setText(editRecipe.getType(), false); //Il filtro va messo su falso , altrimenti il menu a tendina mostra solo l'elemento impostato tramite questa istruzione

            //Popola la lista degli step con le sezioni estratte dall'oggetto editRecipe
            ArrayList<Step> steps = new ArrayList<>();
            ArrayList<Section> sections = editRecipe.getSections();
            for (int i = 0; i < sections.size(); i++) {
                Section temp = sections.get(i);
                int hours = temp.getTimer() / 3600;
                int minutes = (temp.getTimer() % 3600) / 60;
                steps.add(new Step(temp.getText(), temp.getImageUrl(), String.valueOf(hours), String.valueOf(minutes)));
            }
            //Imposta l'immagine di anteprima
            Glide.with(this)
                    .load(editRecipe.getPreviewUrl())
                    .apply(RequestOptions.skipMemoryCacheOf(true))
                    .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.NONE))
                    .centerCrop()
                    .into(imgPreview);
            //Passa la lista degli ingredienti e degli step ai corrispondenti adapter
            mAdapter = new StepAdapter(steps, this);
            mAdapter.setEditing(true);
            mRecyclerView.setAdapter(mAdapter);
            iAdapter = new IngredientsRecyclerViewAdapter(editRecipe.getIngredients());
            iRecyclerView.setAdapter(iAdapter);
            mRecipe = editRecipe;

        } else { //Se è in corso la creazione di una nuova ricetta
            getSupportActionBar().setTitle("Crea ricetta");
            //Passa la lista degli ingredienti e degli step ai corrispondenti adapter
            ArrayList<Step> tempArray = new ArrayList<>();
            mAdapter = new StepAdapter(new ArrayList<Step>(), this);
            mAdapter.addStep(); //Aggiungi il primo step obbligatorio
            mRecyclerView.setAdapter(mAdapter);
            iAdapter = new IngredientsRecyclerViewAdapter(new ArrayList<Ingredient>());
            iRecyclerView.setAdapter(iAdapter);
            //Crea la ricetta da condividere e si assegnano le informazioni dell'utente
            mRecipe = new Recipe();
            mRecipe.setAuthorName(currentUser.getDisplayName());
            mRecipe.setAuthorId(currentUser.getUid());
            if (currentUser.getPhotoUrl() != null)
                mRecipe.setProfilePicUrl(currentUser.getPhotoUrl().toString());
            else mRecipe.setProfilePicUrl("missingprofile");

            mRecipe.setComments(new ArrayList<Comment>());
        }

        //ClickListener per aggiungere uno step
        btnAddStep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toast.makeText(CreateRecipe.this, "Premuto", Toast.LENGTH_LONG).show(); //per vedere quando viene premuto il bottone
                mAdapter.addStep();
            }
        });

        //ClickListener per aggiungere ingredienti
        btnAddIng.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Controlla che il testo non sia vuoto o contenga solo spazi
                if (ingName.getText().toString().trim().equals("") || ingQuantity.getText().toString().trim().equals("")) {
                    Toast.makeText(CreateRecipe.this, R.string.ing_required, Toast.LENGTH_LONG).show();
                } else {  //Metodo per aggiungere un ingrediente alla recycler view
                    iAdapter.addIngredient(new Ingredient(ingName.getText().toString().trim(), ingQuantity.getText().toString().trim()));
                    ingName.setText("");
                    ingQuantity.setText("");
                }

            }
        });
        //ClickListener per eliminare un ingrediente
        btnDelIng.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iAdapter.delIngredient();
                //metodo per eliminare
            }
        });
        //ClickListener per aggiungere un'immagine di anteprima o da galleria o da fotocamera
        imgPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(ImagePickActivity.getPickImageChooserIntent(CreateRecipe.this, "preview"), PREVIEW_REQUEST);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Termina l'asyncTask alla distruzione dell'activity (se non nullo)
        if (mCheckUploadTask != null) mCheckUploadTask.cancel(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.exitNoSave) { //Esci senza salvare (stessa funzione del premere indietro)
            if (!isUploading) { //Se è in corso l'upload evita che l'utente prema questa opzione
                Toast.makeText(this, R.string.recipe_not_saved, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (id == R.id.exitSave) { //Esegue l'upload e fa partire il task per la condivisione
            if (checkInfoNotComplete()) { //Controlla che le informazioni principali siano tutte inserite
                View view = findViewById(R.id.createRecipeLinearLayout1);
                Snackbar.make(view, R.string.minimum_info_required, Snackbar.LENGTH_LONG).show();
            } else {
                if (!isUploading) {
                    generatedId = randomAlphaNumeric();
                    if (isEditing) folder = currentUser.getUid() + "/" + recipeId;
                    else folder = currentUser.getUid() + "/" + generatedId;
                    if (previewUri != null) {
                        main.setUrl("");
                        ImagePickActivity.uploadToStorage(this, previewUri, folder, "preview", main);
                    }
                    for (int i = 0; i < mAdapter.getItemCount(); i++) {
                        if (mAdapter.getSteps().get(i).getHasPicture()) {
                            mAdapter.getSteps().get(i).setUrl("");
                            ImagePickActivity.uploadToStorage(this, mAdapter.getSteps().get(i).getStepUri(), folder, "step" + i, mAdapter.getSteps().get(i));
                        }
                    }
                    isUploading = true;
                    disableEditing(); //Disabilita l'editing durante l'upload
                    View view = findViewById(R.id.createRecipeLinearLayout1);
                    Snackbar up = Snackbar.make(view, R.string.uploading, Snackbar.LENGTH_INDEFINITE);
                    //Si assegna una progress bar circolare alla snackbar
                    ViewGroup contentLay = (ViewGroup) up.getView().findViewById(com.google.android.material.R.id.snackbar_text).getParent();
                    ProgressBar bar = new ProgressBar(this);
                    contentLay.addView(bar);
                    up.show(); //Mostra la snackbar
                    mCheckUploadTask = new CheckUploadTask();
                    mCheckUploadTask.execute(); //Esegui l'AsyncTask
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == PREVIEW_REQUEST && resultCode == RESULT_OK) { //Request dell'immagine di anteprima
            //Prendi l'uri assegnato alla cache
            previewUri = ImagePickActivity.getPickImageResultUri(this, intent, "preview");
            if (previewUri != null) {
                Glide.with(this)
                        .load(previewUri)
                        .apply(RequestOptions.skipMemoryCacheOf(true))
                        .apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.NONE))
                        .centerCrop()
                        .into(imgPreview);
            } else {
                Bitmap bitmap = (Bitmap) intent.getExtras().get("data");
                Glide.with(this)
                        .load(bitmap)
                        .centerCrop()
                        .into(imgPreview);
            }
        }
        if (requestCode == STEP_REQUEST && resultCode == RESULT_OK) { //Request delle'immagini degli step provenienti dallo StepAdapter
            List<Step> templist = mAdapter.getSteps();
            int position = mAdapter.getCurrentPicPosition(); //Per sapere quale elemento della RecyclerView è stato cliccato
            templist.get(position).setHasPicture(true); //Comunica che lo step ha un'immagine e necessita dunque dell'upload
            if (ImagePickActivity.getPickImageResultUri(this, intent, "step" + position) != null) {
                templist.get(position).setStepUri(ImagePickActivity.getPickImageResultUri(this, intent, "step" + position));
                mAdapter.notifyItemChanged(position);
            }
        }
    }

    /**
     * Disabilita l'editing all'inizio dell'upload
     */
    private void disableEditing() {
        imgPreview.setEnabled(false);
        title.setEnabled(false);
        actwType.setEnabled(false);
        totalTime.setEnabled(false);
        ingName.setEnabled(false);
        ingQuantity.setEnabled(false);
        btnAddIng.setEnabled(false);
        btnAddStep.setEnabled(false);
        btnDelIng.setEnabled(false);
        mAdapter.setDisabled(true);
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Funzione per controllare che l'utente abbia inserito tutti le informazioni generali
     *
     * @return risultato booleano del controllo, true se non completo
     */
    private boolean checkInfoNotComplete() {

        boolean preview = (previewUri == null && !isEditing); //Se non è in corso la modifica l'utente deve caricare obbligatoriamente l'immagine di anteprima

        String trimmed = title.getText().toString().trim(); //Controlla che l'utente non abbia inserito solo spazi
        boolean t = trimmed.equals("");

        //Check del primo step di cui la descrizione è obbligatoria
        trimmed = mAdapter.getSteps().get(0).getText().toString().trim(); //Controlla che l'utente non abbia inserito solo spazi
        boolean s = trimmed.equals("");

        //Oltre ai booleani precedenti controlla inoltre che ci sia almeno un ingrediente e sia selezionato il tipo di pietanza
        return preview || t || s || TextUtils.isEmpty(totalTime.getText()) || TextUtils.isEmpty(actwType.getText()) || iAdapter.getItemCount() == 0;
    }

    /**
     * Genera una stringa alfanumerica random per l'id della ricetta
     *
     * @return stringa alfanumerica
     */
    public static String randomAlphaNumeric() {
        String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder builder = new StringBuilder();
        int count = 20;
        while (count-- != 0) {
            int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }

    /**
     * AsyncTask che controlla che tutti gli upload siano completi
     * di seguito avvia una snackbar con pulsante per finalizzare la condivisione (onPostExecute)
     */
    private class CheckUploadTask extends AsyncTask<Void, Integer, Long> {
        @Override
        protected Long doInBackground(Void... voids) {
            boolean finishedUploading = false;
            long totalSize = 0;
            while (!finishedUploading) {
                if (isCancelled()) break;
                if (!main.getUrl().equals("")) {
                    finishedUploading = true;
                    for (int i = 0; i < mAdapter.getItemCount(); i++) {
                        if (mAdapter.getSteps().get(i).getHasPicture() && mAdapter.getSteps().get(i).getUrl().equals("")) {
                            finishedUploading = false;
                            break;
                        }
                    }
                }
            }
            return totalSize;
        }

        @Override
        protected void onPostExecute(Long result) {
            //Authorid e AuthorName in onCreate()
            String titleString = title.getText().toString().trim();
            mRecipe.setTitle(titleString); //Imposta il titolo
            //Suddividi il titolo per poter effettuare la ricerca
            String[] words = titleString.toLowerCase().split(" ");
            mRecipe.setTitleWords(Arrays.asList(words));
            mRecipe.setType(actwType.getText().toString()); //Imposta il tipo di pietanza
            mRecipe.setPreviewUrl(main.getUrl()); //Imposta l'url dell'immagine di anteprima
            mRecipe.setIngredients(iAdapter.getIngredients()); //Imposta gli ingredienti
            //Aggiungi solo i nomi degli ingredienti in minuscolo per effettuare la ricerca
            ArrayList<String> names = new ArrayList<>();
            for (int i = 0; i < iAdapter.getItemCount(); i++) {
                names.add(iAdapter.getIngredients().get(i).getName().toLowerCase());
            }
            mRecipe.setIngNames(names);
            mRecipe.setTime(Integer.parseInt(totalTime.getText().toString())); //Imposta il tempo

            //Aggiunge alla lista di oggetti Section gli step dell'editor con i rispettivi URL e il timer convertito in secondi
            ArrayList<Section> sections = new ArrayList<>();
            List<Step> templist = mAdapter.getSteps();
            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                if(!templist.get(i).getText().trim().equals("") || !templist.get(i).getUrl().trim().equals("")) {
                    int hours = (TextUtils.isEmpty(templist.get(i).getHours())) ? 0 : Integer.parseInt(templist.get(i).getHours());
                    int minutes = (TextUtils.isEmpty(templist.get(i).getMinutes())) ? 0 : Integer.parseInt(templist.get(i).getMinutes());
                    int time = hours * 3600 + minutes * 60;
                    sections.add(new Section(templist.get(i).getText().trim(), templist.get(i).getUrl(), time));
                }
            }
            mRecipe.setSections(sections);

            if (isEditing) {
                //aggiorna la ricetta esistente con solo le informazioni modificate per evitare di sovrascrivere ad esempio like e dislike
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("title", titleString);
                map.put("sections", sections);
                map.put("ingredients", iAdapter.getIngredients());
                map.put("ingNames", names);
                map.put("type", actwType.getText().toString());
                map.put("previewUrl", main.getUrl());
                map.put("time", Integer.parseInt(totalTime.getText().toString()));
                map.put("titleWords", Arrays.asList(words));
                db.collection("Recipes").document(recipeId)
                        .set(map, SetOptions.merge())
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(CreateRecipe.this, R.string.done_editing, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
            } else { //Aggiunge una nuova ricetta
                mRecipe.setDate(Timestamp.now().getSeconds());
                db.collection("Recipes").document(generatedId)
                        .set(mRecipe)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                Toast.makeText(CreateRecipe.this, R.string.done_sharing, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        });
            }
        }

        @Override
        protected void onCancelled() { //Nel caso l'activity venga chiusa prima del completamento elimina i file caricati
            super.onCancelled();
            String deletePath = "images/" + currentUser.getUid() + "/" + title.getText().toString().trim();
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference listRef = storage.getReference().child(deletePath);
            //Elimina tutti i file della cartella specificata (La cartella in se viene eliminata da Firebase automaticamente se non contiene più file)
            //Eliminare direttamente la cartella al momento non è possibile
            listRef.listAll()
                    .addOnSuccessListener(new OnSuccessListener<ListResult>() {
                        @Override
                        public void onSuccess(ListResult listResult) {
                            for (StorageReference prefix : listResult.getPrefixes()) {
                                //Prefixes sono le cartelle
                            }

                            for (StorageReference item : listResult.getItems()) {
                                item.delete();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                        }
                    });
        }

    }

}
