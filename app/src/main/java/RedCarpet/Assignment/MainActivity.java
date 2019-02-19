package RedCarpet.Assignment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.camerakit.CameraKitView;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import id.zelory.compressor.Compressor;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

	private static String TAG = MainActivity.class.getSimpleName();

	private Toolbar toolbar;

	private CoordinatorLayout coordinator_layout;

	private CameraKitView camera_view;
	private ImageView camera_status_icon;
	private TextView camera_status;

	private TextInputLayout input_phone_layout;
	private TextInputEditText input_phone;
	private TextInputLayout input_phone_otp_layout;
	private TextInputEditText input_phone_otp;
	private ImageView phone_status_icon;
	private TextView phone_status;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		coordinator_layout = findViewById(R.id.coordinator_layout);

		camera_view = findViewById(R.id.camera_view);
		camera_status_icon = findViewById(R.id.camera_status_icon);
		camera_status = findViewById(R.id.camera_status);

		findViewById(R.id.fab_camera).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				updateCamera();
			}
		});

		input_phone_layout = findViewById(R.id.input_phone_layout);
		input_phone = findViewById(R.id.input_phone);
		input_phone_otp_layout = findViewById(R.id.input_phone_otp_layout);
		input_phone_otp = findViewById(R.id.input_phone_otp);
		phone_status_icon = findViewById(R.id.phone_status_icon);
		phone_status = findViewById(R.id.phone_status);

		findViewById(R.id.fab_phone).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				updatePhone();
			}
		});

		updateCamera();
	}

	@Override
	protected void onStart() {
		super.onStart();

		camera_view.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (isCameraActive)
			camera_view.onResume();
	}

	@Override
	protected void onPause() {
		camera_view.onPause();

		super.onPause();
	}

	@Override
	protected void onStop() {
		camera_view.onStop();

		super.onStop();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		camera_view.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	//region Camera

	private boolean isCameraActive = false;

	private void updateCamera() {
		if (isCameraActive) {
			updateCameraStatus("Camera capturing... [hold still]", true);

			camera_view.captureImage(new CameraKitView.ImageCallback() {
				@Override
				public void onImage(CameraKitView cameraKitView, byte[] bytes) {
					captureImage(bytes);

					camera_view.onPause();

					updateCameraStatus("Camera Off.", false);
				}
			});
		} else {
			camera_view.onResume();

			updateCameraStatus("Camera On.", true);
		}

		isCameraActive = !isCameraActive;
	}

	private void updateCameraStatus(String status, boolean flashing) {
		camera_status.setText(status);

		if (flashing) {
			Animation animation = new AlphaAnimation(0.0f, 1.0f);
			animation.setDuration(250);
			animation.setStartOffset(20);
			animation.setRepeatMode(Animation.REVERSE);
			animation.setRepeatCount(Animation.INFINITE);

			camera_status_icon.startAnimation(animation);
		} else {
			camera_status_icon.clearAnimation();
		}
	}

	private Disposable captureSubscription = null;

	private File photoFile;

	private void captureImage(final byte[] bytes) {
		if (captureSubscription != null) {
			showMessage("Image compressor is still working. Please wait.");
			return;
		}

		Observable<File> saveBitmapObservable = Observable.create(new ObservableOnSubscribe<File>() {
			@Override
			public void subscribe(ObservableEmitter<File> oe) throws Exception {
				try {
					// Decode bytes
					Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

					// Create temporary file for compression
					File temp = File.createTempFile("img", "jpg", MainActivity.this.getCacheDir());

					// Save ~uncompressed
					try (OutputStream outStream = new FileOutputStream(temp)) {
						bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
					} catch (Exception e) {
						throw e;
					}
					oe.onNext(temp);

					oe.onComplete();
				} catch (Exception e) {
					oe.onError(e);
				}
			}
		});

		Observable compressBitmapFile = saveBitmapObservable.flatMap(new Function<File, ObservableSource<File>>() {
			@Override
			public ObservableSource<File> apply(File result) {
				return new Compressor(MainActivity.this)
						.compressToFileAsFlowable(result)
						.toObservable();
			}
		});

		updateCameraStatus("Image compressor working...", true);

		captureSubscription = compressBitmapFile
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Consumer<File>() {
					@Override
					public void accept(File result) {
						photoFile = result;

						updateCameraStatus("Image compressed. Ready to upload.", false);

						showMessage("Image compressed. Ready to upload.");

						captureSubscription = null;
					}
				}, new Consumer<Throwable>() {
					@Override
					public void accept(Throwable throwable) {
						throwable.printStackTrace();

						updateCameraStatus("Image compression failed.", false);

						showMessage("An error caught!");

						captureSubscription = null;
					}
				});
	}

	//endregion

	//region Phone + OTP

	private boolean isPhoneActive = false;

	private String phone;

	private void updatePhone() {
		if (photoFile == null) {
			showMessage("Take a photo please!");
			return;
		}

		if (isPhoneOTPVerificationInProgress) {
			String code = input_phone_otp.getText().toString().trim();

			verifyPhoneNumberWithCode(verificationId, code);
		} else {
			checkPhoneInDB();
		}
	}

	private void updatePhoneStatus(String status, boolean flashing) {
		phone_status.setText(status);

		if (flashing) {
			Animation animation = new AlphaAnimation(0.0f, 1.0f);
			animation.setDuration(250);
			animation.setStartOffset(20);
			animation.setRepeatMode(Animation.REVERSE);
			animation.setRepeatCount(Animation.INFINITE);

			phone_status_icon.startAnimation(animation);
		} else {
			phone_status_icon.clearAnimation();
		}
	}

	private static final String DB_KEY_VISITORS = "visitors";
	private static final String DB_KEY_SUSPICIOUS_USERS = "suspicious_users";
	private static final String DB_KEY_PHONE = "phone";
	private static final String DB_KEY_COUNT = "count";
	private static final String DB_KEY_PHOTO = "photo";

	private void checkPhoneInDB() {
		if (isPhoneActive) {
			showMessage("Phone checking is still going on. Please wait.");
			return;
		}

		isPhoneActive = true;

		phone = input_phone.getText().toString().trim();
		if (TextUtils.isEmpty(phone) || !TextUtils.isDigitsOnly(phone) || phone.length() != 10) {
			input_phone_layout.setError("Invalid phone number.");

			isPhoneActive = false;
			return;
		}

		phone = "+91" + phone;

		updatePhoneStatus("Checking...", true);

		DatabaseReference db = FirebaseDatabase.getInstance().getReference();
		DatabaseReference ref = db.child(DB_KEY_VISITORS);

		Query query = ref.orderByChild(DB_KEY_PHONE).equalTo(phone);
		query.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(@NotNull DataSnapshot dataSnapshot) {
				DataSnapshot found = null;

				for (DataSnapshot child : dataSnapshot.getChildren()) {
					found = child; // Should be one only
				}

				if (found != null) {
					updatePhoneStatus("Found! Please wait...", false);

					updateOldVisitor(Objects.requireNonNull(found.getKey()));
				} else {
					updatePhoneStatus("Welcome! Please wait...", true);

					startPhoneVerification(phone);
				}
			}

			@Override
			public void onCancelled(@NotNull DatabaseError databaseError) {
				Log.e(TAG, "onCancelled", databaseError.toException());

				updatePhoneStatus("An error caught!", false);
			}
		});
	}

	//region Database

	private void updateOldVisitor(String key) {
		DatabaseReference db = FirebaseDatabase.getInstance().getReference();
		DatabaseReference ref = db.child(DB_KEY_VISITORS)
				.child(key)
				.child(DB_KEY_COUNT);

		ref.runTransaction(new Transaction.Handler() {
			@NonNull
			@Override
			public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
				try {
					Long value = mutableData.getValue(Long.class);
					if (value == null) {
						mutableData.setValue(0);
					} else {
						mutableData.setValue(value + 1);
					}

					return Transaction.success(mutableData);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return Transaction.abort();
			}

			@Override
			public void onComplete(@Nullable DatabaseError databaseError, boolean status, @Nullable DataSnapshot dataSnapshot) {
				Long value = null;
				if (dataSnapshot != null) {
					value = dataSnapshot.getValue(Long.class);
				}

				if (databaseError != null || value == null) {
					updatePhoneStatus("An error caught!", false);

					showMessage("Err! Unable to update visitor!");
				} else {
					updatePhoneStatus("Saved!", false);

					showMessage("Welcome back for " + value + GetOrdinalSuffix(value) + " time!");
				}

				isPhoneActive = false;
			}
		});
	}

	private void createNewVisitor(final String collection) {
		updateCameraStatus("Working...", true);
		updatePhoneStatus("Working...", true);
		showMessage("Working...");

		// Create a unique key
		final DatabaseReference db = FirebaseDatabase.getInstance().getReference();
		final String key = db.child(collection).push().getKey();

		// Start uploading photo
		Uri file = Uri.fromFile(photoFile);
		StorageReference storageRef = FirebaseStorage.getInstance().getReference();
		final StorageReference imageRef = storageRef.child(collection).child(key);

		UploadTask uploadTask = imageRef.putFile(file);
		uploadTask.addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				exception.printStackTrace();

				updateCameraStatus("Uploading failed!", false);

				if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
					showMessage("Uploading failed!");
			}
		}).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
			@Override
			public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
				updateCameraStatus("Uploading completed!", false);

				if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
					showMessage("Uploading completed! Please wait...");
			}
		}).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
			@Override
			public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
				int percent = (int) ((float) taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
				updateCameraStatus("Uploading " + percent + "%...", true);

				if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
					showMessage("Uploading " + percent + "%...");
			}
		});

		Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
			@Override
			public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
				if (!task.isSuccessful()) {
					throw Objects.requireNonNull(task.getException());
				}

				return imageRef.getDownloadUrl();
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				exception.printStackTrace();
			}
		}).addOnSuccessListener(new OnSuccessListener<Uri>() {
			@Override
			public void onSuccess(Uri result) {
			}
		});

		Task<Void> createEntryTask = urlTask.continueWithTask(new Continuation<Uri, Task<Void>>() {
			@Override
			public Task<Void> then(@NonNull Task<Uri> task) throws Exception {
				if (!task.isSuccessful()) {
					throw Objects.requireNonNull(task.getException());
				}

				String photo = Objects.requireNonNull(task.getResult()).toString();

				DatabaseReference ref = db.child(DB_KEY_VISITORS).child(key);

				HashMap<Object, Object> values = new HashMap<>();
				values.put(DB_KEY_PHONE, phone);
				if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
					values.put(DB_KEY_COUNT, 1);
				values.put(DB_KEY_PHOTO, photo);

				ref.setValue(values, new DatabaseReference.CompletionListener() {
					@Override
					public void onComplete(DatabaseError databaseError, @NotNull DatabaseReference databaseReference) {
						if (databaseError != null) {
							updatePhoneStatus("An error caught!", false);

							if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
								showMessage("Err! Unable to save new visitor!");
						} else {
							updatePhoneStatus("Saved!", false);

							if (!collection.equals(DB_KEY_SUSPICIOUS_USERS))
								showMessage("Welcome! New visitor saved!");
						}
					}
				});

				return null;
			}
		}).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception exception) {
				exception.printStackTrace();

				isPhoneActive = false;
			}
		}).addOnSuccessListener(new OnSuccessListener<Void>() {
			@Override
			public void onSuccess(Void result) {
			}
		}).addOnCompleteListener(new OnCompleteListener<Void>() {
			@Override
			public void onComplete(@NonNull Task<Void> task) {
				isPhoneActive = false;
			}
		});
	}

	//endregion

	//region Phone Verification

	private long phoneVerificationTime = 0;
	private boolean isPhoneOTPVerificationInProgress = false;
	private String verificationId;
	private PhoneAuthProvider.ForceResendingToken forceResendingToken;

	private PhoneAuthProvider.OnVerificationStateChangedCallbacks phoneVerificationStateChangedCallbacks =
			new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
				@Override
				public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {
					// Should not be called
				}

				@Override
				public void onVerificationFailed(FirebaseException e) {
					isPhoneOTPVerificationInProgress = false;

					showMessage("Err! " + e.getMessage());

					showMessage("Verification failed!");

					createNewVisitor(DB_KEY_SUSPICIOUS_USERS);
				}

				@Override
				public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
					startPhoneOTPTimer();

					updatePhoneStatus("Enter received OTP...", true);

					MainActivity.this.verificationId = verificationId;
					MainActivity.this.forceResendingToken = forceResendingToken;
				}

				@Override
				public void onCodeAutoRetrievalTimeOut(String s) {
					super.onCodeAutoRetrievalTimeOut(s);
				}
			};

	private void startPhoneVerification(String phone) {
		isPhoneOTPVerificationInProgress = true;

		updatePhoneStatus("Sending OTP...", true);

		input_phone_layout.setVisibility(View.INVISIBLE);

		PhoneAuthProvider.getInstance().verifyPhoneNumber(
				phone,
				0,
				TimeUnit.SECONDS,
				this,
				phoneVerificationStateChangedCallbacks);

		input_phone_otp_layout.setVisibility(View.VISIBLE);
	}

	private void resendVerificationCode(String phone, PhoneAuthProvider.ForceResendingToken token) {
		isPhoneOTPVerificationInProgress = true;

		PhoneAuthProvider.getInstance().verifyPhoneNumber(
				phone,
				0,
				TimeUnit.SECONDS,
				this,
				phoneVerificationStateChangedCallbacks,
				token);
	}

	private CountDownTimer phoneOTPTimeoutTimer;

	private void startPhoneOTPTimer() {
		phoneVerificationTime = System.currentTimeMillis();

		phoneOTPTimeoutTimer = new CountDownTimer(30 * 1000, 1000) {
			public void onTick(long millisUntilFinished) {
				if (isPhoneOTPVerificationInProgress)
					updatePhoneStatus("Enter received OTP... [" + (millisUntilFinished / 1000) + "s left]", true);
				else
					phoneOTPTimeoutTimer.onFinish();
			}

			public void onFinish() {
				if (isPhoneOTPVerificationInProgress)
					verifyPhoneNumberWithCode(verificationId, input_phone_otp.getText().toString().trim());
			}
		};

		phoneOTPTimeoutTimer.start();
	}

	private void verifyPhoneNumberWithCode(String verificationId, String code) {
		if (((phoneVerificationTime + 30 * 1000) < System.currentTimeMillis())
				|| TextUtils.isEmpty(code)
				|| code.length() < 6
				|| TextUtils.isEmpty(verificationId)) {
			showMessage("Verification failed! Time out!");

			createNewVisitor(DB_KEY_SUSPICIOUS_USERS);

			input_phone_layout.setVisibility(View.VISIBLE);
			input_phone_otp_layout.setVisibility(View.INVISIBLE);

			return;
		}

		PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);

		FirebaseAuth.getInstance().signInWithCredential(credential)
				.addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
					@Override
					public void onComplete(@NonNull Task<AuthResult> task) {
						isPhoneOTPVerificationInProgress = false;

						if (task.isSuccessful()) {
							showMessage("Verification successful!");

							createNewVisitor(DB_KEY_VISITORS);

						} else {
							showMessage("Verification failed!");

							createNewVisitor(DB_KEY_SUSPICIOUS_USERS);
						}

						input_phone_layout.setVisibility(View.VISIBLE);
						input_phone_otp_layout.setVisibility(View.INVISIBLE);
					}
				});
	}

	//endregion

	//endregion

	private void showMessage(String message) {
		Snackbar.make(coordinator_layout, message, Snackbar.LENGTH_INDEFINITE)
				.show();
	}

	private String GetOrdinalSuffix(Long value) {
		if (value.toString().endsWith("11")) return "th";
		if (value.toString().endsWith("12")) return "th";
		if (value.toString().endsWith("13")) return "th";
		if (value.toString().endsWith("1")) return "st";
		if (value.toString().endsWith("2")) return "nd";
		if (value.toString().endsWith("3")) return "rd";
		return "th";
	}
}
