package id.deuboe.friendlychat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint.FontMetrics;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;
import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.firebase.ui.database.SnapshotParser;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask.TaskSnapshot;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.Objects;

public class MainActivity extends AppCompatActivity
    implements GoogleApiClient.OnConnectionFailedListener {

  public static class MessageViewHolder extends RecyclerView.ViewHolder {
    private TextView textMessage;
    private ImageView imageMessage;
    private TextView textMessenger;
    private CircleImageView imageMessenger;

    MessageViewHolder(@NonNull View v) {
      super(v);
      textMessage = itemView.findViewById(R.id.text_message);
      imageMessage = itemView.findViewById(R.id.image_message);
      textMessenger = itemView.findViewById(R.id.text_messenger);
      imageMessenger = itemView.findViewById(R.id.image_messenger);
    }
  }

  private static final String TAG = "MainActivity";
  public static final String MESSAGES_CHILD = "messages";
  public static final String ANONYMOUS = "anonymous";
  private static final int REQUEST_IMAGE = 2;
  private static final String LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif";
  private String mUsername;
  private String mPhotoUrl;
  private SharedPreferences mSharedPreferences;
  private GoogleApiClient mGoogleApiClient;

  private AppCompatButton mSendButton;
  private AppCompatEditText mMessageEditText;
  private AppCompatImageView mAddImageMessage;
  private RecyclerView mMessageRecyclerView;
  private LinearLayoutManager mLinearLayoutManager;
  private ProgressBar mProgressBar;

  //  Firebase instance variables
  private FirebaseAuth mFirebaseAuth;
  private FirebaseUser mFirebaseUser;
  private DatabaseReference mFirebaseDatabaseReference;
  private FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> mFirebaseAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    // set default username is anonymous
    mUsername = ANONYMOUS;

    // Initialize Firebase Auth
    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseUser = mFirebaseAuth.getCurrentUser();
    if (mFirebaseUser == null) {
    // Not Sign in, Launch Sign In Activity
      startActivity(new Intent(this, SignInActivity.class));
      finish();
    } else {
      mUsername = mFirebaseUser.getDisplayName();
      if (mFirebaseUser.getPhotoUrl() != null) {
        mPhotoUrl = mFirebaseUser.getPhotoUrl().toString();
      }
    }

    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .enableAutoManage(this, this)
        .addApi(Auth.GOOGLE_SIGN_IN_API)
        .build();

    // Initialize ProgressBar and RecyclerView
    mProgressBar = findViewById(R.id.progressBar);
    mMessageRecyclerView = findViewById(R.id.recyclerView_message);
    mLinearLayoutManager = new LinearLayoutManager(this);
    mLinearLayoutManager.setStackFromEnd(true);
    mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);

    mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
    SnapshotParser<FriendlyMessage> parser = new SnapshotParser<FriendlyMessage>() {
      @NonNull
      @Override
      public FriendlyMessage parseSnapshot(@NonNull DataSnapshot snapshot) {
        FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
        if (friendlyMessage != null) {
          friendlyMessage.setId(snapshot.getKey());
        }
        return Objects.requireNonNull(friendlyMessage);
      }
    };

    DatabaseReference messagesRef = mFirebaseDatabaseReference.child(MESSAGES_CHILD);
    FirebaseRecyclerOptions<FriendlyMessage> options =
        new FirebaseRecyclerOptions.Builder<FriendlyMessage>()
        .setQuery(messagesRef, parser)
        .build();

    mFirebaseAdapter = new FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(options) {
      @Override
      protected void onBindViewHolder(@NonNull final MessageViewHolder holder, int position,
          @NonNull FriendlyMessage model) {
          mProgressBar.setVisibility(View.INVISIBLE);
          if (model.getText() != null) {
            holder.textMessage.setText(model.getText());
            holder.textMessage.setVisibility(TextView.VISIBLE);
            holder.imageMessage.setVisibility(ImageView.GONE);
          } else if (model.getImageUrl() != null) {
            String imageUrl = model.getImageUrl();
            if (imageUrl.startsWith("gs://")) {
              StorageReference storageReference = FirebaseStorage.getInstance()
                  .getReferenceFromUrl(imageUrl);
              storageReference.getDownloadUrl().addOnCompleteListener(
                  new OnCompleteListener<Uri>() {
                    @Override
                    public void onComplete(@NonNull Task<Uri> task) {
                      if (task.isSuccessful()) {
                        String downloadUrl = task.getResult().toString();
                        Glide.with(holder.imageMessage.getContext())
                            .load(downloadUrl)
                            .into(holder.imageMessage);
                      } else {
                        Log.w(TAG, "Getting download url was not successful.",
                            task.getException());
                      }
                    }
                  });
            } else {
              Glide.with(holder.imageMessage.getContext())
                  .load(model.getImageUrl())
                  .into(holder.imageMessage);
            }
            holder.imageMessage.setVisibility(ImageView.VISIBLE);
            holder.textMessage.setVisibility(TextView.GONE);
          }

          holder.textMessenger.setText(model.getName());
          if (model.getPhotoUrl() == null) {
            holder.imageMessenger.setImageDrawable(ContextCompat.getDrawable(MainActivity.this,
                R.drawable.ic_account_circle_black_36dp));
          } else {
            Glide.with(MainActivity.this)
                .load(model.getPhotoUrl())
                .into(holder.imageMessenger);
          }
      }

      @NonNull
      @Override
      public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new MessageViewHolder(
            inflater.inflate(R.layout.item_message, parent, false)
        );
      }
    };

    mFirebaseAdapter.registerAdapterDataObserver(new AdapterDataObserver() {
      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        super.onItemRangeInserted(positionStart, itemCount);
        int friendlyMessageCount = mFirebaseAdapter.getItemCount();
        int lastVisiblePosition = mLinearLayoutManager.findLastCompletelyVisibleItemPosition();

        if (lastVisiblePosition == -1 || (positionStart >= (friendlyMessageCount -1)
            && lastVisiblePosition == (positionStart -1))) {
          mMessageRecyclerView.scrollToPosition(positionStart);
        }
      }
    });

    mMessageRecyclerView.setAdapter(mFirebaseAdapter);

    mMessageEditText = findViewById(R.id.edit_message);
    mMessageEditText.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {

      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (s.toString().trim().length() > 0) {
          mSendButton.setEnabled(true);
        } else {
          mSendButton.setEnabled(false);
        }
      }

      @Override
      public void afterTextChanged(Editable s) {

      }
    });

    mSendButton = findViewById(R.id.button_send);
    mSendButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),
            mUsername,
            mPhotoUrl,
            null);
        mFirebaseDatabaseReference.child(MESSAGES_CHILD)
            .push().setValue(friendlyMessage);
        mMessageEditText.setText("");
      }
    });

    mAddImageMessage = findViewById(R.id.image_add_image_message);
    mAddImageMessage.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
      }
    });
  }

  @Override
  protected void onPause() {
    mFirebaseAdapter.stopListening();
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mFirebaseAdapter.startListening();
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
      case R.id.sign_out_menu:
        mFirebaseAuth.signOut();
        Auth.GoogleSignInApi.signOut(mGoogleApiClient);
        mUsername = ANONYMOUS;
        startActivity(new Intent(this, SignInActivity.class));
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    Log.d(TAG, "onActivityResult: requestCode= " + requestCode + ", resultCode= " + resultCode);

    if (requestCode == REQUEST_IMAGE) {
      if (resultCode == RESULT_OK) {
        if (data != null) {
          final Uri uri = data.getData();
          Log.d(TAG, "Uri: " + uri.toString());

          FriendlyMessage tempMessage = new FriendlyMessage(null, mUsername, mPhotoUrl,
              LOADING_IMAGE_URL);
          mFirebaseDatabaseReference.child(MESSAGES_CHILD).push()
              .setValue(tempMessage, new CompletionListener() {
                @Override
                public void onComplete(@Nullable DatabaseError databaseError,
                    @NonNull DatabaseReference databaseReference) {
                  if (databaseError == null) {
                    String key = databaseReference.getKey();
                    StorageReference storageReference = FirebaseStorage.getInstance()
                        .getReference(mFirebaseUser.getUid())
                        .child(key)
                        .child(uri.getLastPathSegment());

                    putImageInStorage(storageReference, uri, key);
                  } else {
                    Log.w(TAG, "Unable to write message to database.",
                        databaseError.toException());
                  }
                }
              });
        }
      }
    }
  }

  private void putImageInStorage(StorageReference storageReference, Uri uri, final String key) {
    storageReference.putFile(uri).addOnCompleteListener(MainActivity.this,
        new OnCompleteListener<TaskSnapshot>() {
          @Override
          public void onComplete(@NonNull Task<TaskSnapshot> task) {
            if (task.isSuccessful()) {
              task.getResult().getMetadata().getReference().getDownloadUrl()
                  .addOnCompleteListener(MainActivity.this,
                      new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                          if (task.isSuccessful()) {
                            FriendlyMessage friendlyMessage =
                                new FriendlyMessage(null, mUsername, mPhotoUrl,
                                    task.getResult().toString());
                            mFirebaseDatabaseReference.child(MESSAGES_CHILD).child(key)
                                .setValue(friendlyMessage);
                          }
                        }
                      });
            } else {
              Log.w(TAG, "Image upload task was not successful.", task.getException());
            }
          }
        });
  }
}
