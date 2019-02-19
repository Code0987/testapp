package RedCarpet.Assignment;

import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

public class App extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		// DB
		try {
			FirebaseDatabase.getInstance().setPersistenceEnabled(false);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
