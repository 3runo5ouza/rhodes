/*
 ============================================================================
 Author	    : Anton Antonov
 Version	: 1.0
 Copyright  : Copyright (C) 2008 Rhomobile. All rights reserved.

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ============================================================================
 */
package com.rhomobile.rhodes;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

import com.rhomobile.rhodes.Utils.AssetsSource;
import com.rhomobile.rhodes.Utils.FileSource;
import com.rhomobile.rhodes.geolocation.GeoLocation;
import com.rhomobile.rhodes.mainview.MainView;
import com.rhomobile.rhodes.ui.AboutDialog;
import com.rhomobile.rhodes.ui.LogOptionsDialog;
import com.rhomobile.rhodes.ui.LogViewDialog;
import com.rhomobile.rhodes.uri.MailUriHandler;
import com.rhomobile.rhodes.uri.SmsUriHandler;
import com.rhomobile.rhodes.uri.TelUriHandler;
import com.rhomobile.rhodes.uri.UriHandler;
import com.rhomobile.rhodes.uri.VideoUriHandler;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.os.Process;

public class Rhodes extends Activity {

	private static final String TAG = "Rhodes";
	
	public static final String INTENT_EXTRA_PREFIX = "com.rhomobile.rhodes.";
	
	public static final int RHO_SPLASH_VIEW = 1;
	public static final int RHO_MAIN_VIEW = 2;
	public static final int RHO_TOOLBAR_VIEW = 3;
	
	public static int WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_FULLSCREEN;
	public static int WINDOW_MASK = WindowManager.LayoutParams.FLAG_FULLSCREEN;
	
	private static int MAX_PROGRESS = 10000;
	
	private static boolean ENABLE_LOADING_INDICATION = true;
	
	private boolean needGeoLocationRestart = false;
	
	private long uiThreadId;
	public long getUiThreadId() {
		return uiThreadId;
	}
	
	private final Handler uiHandler = new Handler();
	
	private static int screenWidth;
	private static int screenHeight;
	
	private static float screenPpiX;
	private static float screenPpiY;
	
	private static boolean isCameraAvailable;

	private FrameLayout outerFrame;
	private MainView mainView;
	
	private SplashScreen splashScreen = null;
	
	private Boolean contentChanged = null;
	
	private Vector<UriHandler> uriHandlers = new Vector<UriHandler>();

	//private String sdCardError = "Application can not access the SD card while it's mounted. Please unmount the device and stop the adb server before launching the app.";
	
	private RhoMenu appMenu = null;
		
	private String rootPath = null;
	
	public native void createRhodesApp(String rootPath, String sqliteJournalsPath);
	public native void startRhodesApp();
	public native void stopRhodesApp();
	
	public native void doSyncAllSources(boolean v);
	
	public native String getOptionsUrl();
	public native String getStartUrl();
	public native String getCurrentUrl();
	public native String getAppBackUrl();
	
	public native String normalizeUrl(String url);
	
	public static native void loadUrl(String url);
	
	public static native void navigateBack();
	
	public native void doRequest(String url);
	
	public native static void makeLink(String src, String dst);
	
	private native void initClassLoader(ClassLoader c);
	
	private void initRootPath() {
		Log.d(TAG, "Check if the SD card is mounted...");
		String state = Environment.getExternalStorageState();
		Log.d(TAG, "Storage state: " + state);
		boolean hasSDCard = Environment.MEDIA_MOUNTED.equals(state);
		rootPath = hasSDCard ? sdcardRootPath() : phoneMemoryRootPath();
	}
	
	public String getRootPath() {
		return rootPath;
	}
	
	private ApplicationInfo getAppInfo() {
		String pkgName = getPackageName();
		try {
			ApplicationInfo info = getPackageManager().getApplicationInfo(pkgName, 0);
			return info;
		} catch (NameNotFoundException e) {
			throw new RuntimeException("Internal error: package " + pkgName + " not found: " + e.getMessage());
		}
	}
	
	private String getSqliteJournalsPath() {
		String path = getAppInfo().dataDir + "/sqlite_stmt_journals/";
		new File(path).mkdirs();
		return path;
	}
	
	private String phoneMemoryRootPath() {
		return getAppInfo().dataDir + "/rhodata/";
	}
	
	private String sdcardRootPath() {
		String sdPath = Environment.getExternalStorageDirectory().getAbsolutePath();
		String pkgName = getPackageName();
		String path = sdPath + "/rhomobile/" + pkgName + "/";
		return path;
	}
	
	public static String getBlobPath() {
		return RhodesInstance.getInstance().getRootPath() + "/db/db-files";
	}
	
	private RhoLogConf m_rhoLogConf = new RhoLogConf();
	public RhoLogConf getLogConf() {
		return m_rhoLogConf;
	}

	/*
	private boolean checkSDCard() {
		Log.d(TAG, "Check if the SD card is mounted...");
		String state = Environment.getExternalStorageState();
		Log.d(TAG, "Storage state: " + state);
		if(!Environment.MEDIA_MOUNTED.equals(state)) {
			new AlertDialog.Builder(this)
				.setTitle("SD card error")
				.setMessage(sdCardError)
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.e(this.getClass().getSimpleName(), "Exit - SD card is not accessible");
						Process.killProcess(Process.myPid());
					}					
				})
				.create()
				.show();
			return false;
		}
		Log.d(TAG, "SD card check passed, going on");
		return true;
	}
	*/
	
	private void copyFromBundle(String file) throws IOException {
		File target = new File(getRootPath(), file);
		if (target.exists())
			return;
		FileSource as = new AssetsSource(getResources().getAssets());
		Utils.copyRecursively(as, file, target, true);
		
		/*
		int idx = file.indexOf('/');
		String dir = idx == -1 ? file : file.substring(0, idx);
		
		File sdPath = new File(sdcardRootPath(), dir);
		File phPath = new File(phoneMemoryRootPath(), dir);
		phPath.getParentFile().mkdirs();
		makeLink(sdPath.getAbsolutePath(), phPath.getAbsolutePath());
		*/
	}
	
	public boolean isNameChanged() {
		try {
			FileSource as = new AssetsSource(getResources().getAssets());
			FileSource fs = new FileSource();
			return !Utils.isContentsEquals(as, "name", fs, new File(getRootPath(), "name").getPath());
		}
		catch (IOException e) {
			return true;
		}
	}
	
	public boolean isBundleChanged() {
		if (contentChanged == null) {
			try {
				String rp = getRootPath();
				
				FileSource as = new AssetsSource(getResources().getAssets());
				FileSource fs = new FileSource();
				
				if (isNameChanged())
					contentChanged = new Boolean(true);
				else
					contentChanged = new Boolean(!Utils.isContentsEquals(as, "hash", fs, new File(rp, "hash").getPath()));
			}
			catch (IOException e) {
				contentChanged = new Boolean(true);
			}
		}
		return contentChanged.booleanValue();
	}
	
	private void copyFilesFromBundle() {
		try {
			if (isBundleChanged()) {
				Logger.D(TAG, "Copying required files from bundle");
				
				boolean nameChanged = isNameChanged();
				
				String rp = getRootPath();
				
				FileSource as = new AssetsSource(getResources().getAssets());

				String items[] = {"apps", "lib", "db", "hash", "name"};
				for (int i = 0; i != items.length; ++i) {
					String item = items[i];
					File f = new File(rp, item);
					Logger.D(TAG, "Copy '" + item + "' to '" + f.getAbsolutePath() + "'");
					Utils.copyRecursively(as, item, f, nameChanged);
					/*
					String src = sdf.getAbsolutePath();
					String dst = phf.getAbsolutePath();
					Logger.D(TAG, "Make symlink from '" + src + "' to '" + dst + "'");
					makeLink(src, dst);
					*/
				}
				
				contentChanged = new Boolean(true);
				Logger.D(TAG, "All files copied");
			}
			else
				Logger.D(TAG, "No need to copy files to SD card");
		} catch (IOException e) {
			Logger.E(TAG, e);
			return;
		}
	}
	
	private boolean handleUrlLoading(String url) {
		Enumeration<UriHandler> e = uriHandlers.elements();
		while (e.hasMoreElements()) {
			UriHandler handler = e.nextElement();
			try {
				if (handler.handle(url))
					return true;
			}
			catch (Exception ex) {
				continue;
			}
		}
		
		return false;
	}
	
	public WebView createWebView() {
		WebView w = new WebView(this);
		
		WebSettings webSettings = w.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
		webSettings.setSupportZoom(false);
		webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
		webSettings.setSupportMultipleWindows(false);
		// webSettings.setLoadsImagesAutomatically(true);

		w.setVerticalScrollBarEnabled(true);
		w.setHorizontalScrollBarEnabled(true);
		w.setVerticalScrollbarOverlay(true);
		w.setHorizontalScrollbarOverlay(true);
		w.setFocusableInTouchMode(true);

		w.setWebViewClient(new WebViewClient() {
			
			private boolean splashHidden = false;

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				return handleUrlLoading(url);
			}
			
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				if (ENABLE_LOADING_INDICATION)
					getWindow().setFeatureInt(Window.FEATURE_PROGRESS, 0);
				super.onPageStarted(view, url, favicon);
			}
			
			@Override
			public void onPageFinished(WebView view, String url) {
				// Set title
				Rhodes r = RhodesInstance.getInstance();
				String title = view.getTitle();
				r.setTitle(title);
				// Hide splash screen
				if (!splashHidden && url.startsWith("http://")) {
					hideSplashScreen();
					splashHidden = true;
				}
				if (ENABLE_LOADING_INDICATION)
					getWindow().setFeatureInt(Window.FEATURE_PROGRESS, MAX_PROGRESS);
				super.onPageFinished(view, url);
			}

		});
		
		w.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				if (ENABLE_LOADING_INDICATION) {
					newProgress *= 100;
					if (newProgress < 0)
						newProgress = 0;
					if (newProgress > MAX_PROGRESS)
						newProgress = MAX_PROGRESS;
					getWindow().setFeatureInt(Window.FEATURE_PROGRESS, newProgress);
				}
				super.onProgressChanged(view, newProgress);
			}
			
			@Override
			public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
				return false;
			}
			
			@Override
			public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
				return false;
			}
		});

		return w;
	}
	
	public void setMainView(MainView v) {
		View main = outerFrame.findViewById(RHO_MAIN_VIEW);
		if (main != null)
			outerFrame.removeView(main);
		mainView = v;
		main = mainView.getView();
		if (outerFrame.findViewById(RHO_SPLASH_VIEW) != null)
			main.setVisibility(View.INVISIBLE);
		outerFrame.addView(main);
	}
	
	public MainView getMainView() {
		return mainView;
	}
	
	private static class PerformOnUiThread implements Runnable {
		
		private Runnable runnable;
		
		public PerformOnUiThread(Runnable r) {
			runnable = r;
		}
		
		public void run() {
			try {
				runnable.run();
			}
			catch (Exception e) {
				Logger.E("Rhodes", "PerformOnUiThread failed: " + e.getMessage());
			}
			finally {
				synchronized (runnable) {
					runnable.notify();
				}
			}
		}
	};
	
	public static void performOnUiThread(Runnable r, boolean wait) {
		try {
			Rhodes rhodes = RhodesInstance.getInstance();
			if (!wait) {
				rhodes.uiHandler.post(r);
			}
			else {
				long thrId = Thread.currentThread().getId();
				if (rhodes.getUiThreadId() == thrId) {
					// We are already in UI thread
					r.run();
				}
				else {
					// Post request to UI thread and wait when it would be done
					synchronized (r) {
						rhodes.uiHandler.post(new PerformOnUiThread(r));
						r.wait();
					}
				}
			}
		}
		catch (Exception e) {
			Logger.E("Rhodes", "performOnUiThread failed: " + e.getMessage());
		}
	}
	
	private void showSplashScreen() {
		splashScreen = new SplashScreen(this);
		splashScreen.start(outerFrame);
	}
	
	public void hideSplashScreen() {
		if (splashScreen != null) {
			splashScreen.hide(outerFrame);
			splashScreen = null;
		}
		View view = mainView.getView();
		view.setVisibility(View.VISIBLE);
		view.requestFocus();
	}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Here Log should be used, not Logger. It is because Logger is not initialized yet.
		Log.v(TAG, "+++ onCreate");
		
		Thread ct = Thread.currentThread();
		ct.setPriority(Thread.MAX_PRIORITY);
		uiThreadId = ct.getId();

		RhodesInstance.setInstance(this);

		initClassLoader(this.getClassLoader());
		
		initRootPath();
		try {
			copyFromBundle("apps/rhoconfig.txt");
		} catch (IOException e1) {
			Logger.E("Rhodes", e1);
			finish();
			return;
		}
		createRhodesApp(getRootPath(), getSqliteJournalsPath());
		
		boolean fullScreen = true;
		if (RhoConf.isExist("full_screen"))
			fullScreen = RhoConf.getBool("full_screen");
		if (!fullScreen) {
			WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
			WINDOW_MASK = WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
		}
		getWindow().setFlags(WINDOW_FLAGS, WINDOW_MASK);

		boolean disableScreenRotation = RhoConf.getBool("disable_screen_rotation");
		this.setRequestedOrientation(disableScreenRotation ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT :
			ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		
		ENABLE_LOADING_INDICATION = !RhoConf.getBool("disable_loading_indication");
		if (ENABLE_LOADING_INDICATION)
			this.requestWindowFeature(Window.FEATURE_PROGRESS);

		outerFrame = new FrameLayout(this);
		this.setContentView(outerFrame, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		Logger.I("Rhodes", "Loading...");
		showSplashScreen();
		
		// Increase WebView rendering priority
		WebView w = new WebView(this);
		WebSettings webSettings = w.getSettings();
		webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
		
		// Get screen width/height
		WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
		Display d = wm.getDefaultDisplay();
		screenHeight = d.getHeight();
		screenWidth = d.getWidth();
		
		DisplayMetrics metrics = new DisplayMetrics();
		d.getMetrics(metrics);
		screenPpiX = metrics.xdpi;
		screenPpiY = metrics.ydpi;
		
		// TODO: detect camera availability
		isCameraAvailable = true;
		
		// Register custom uri handlers here
		uriHandlers.addElement(new MailUriHandler(this));
		uriHandlers.addElement(new TelUriHandler(this));
		uriHandlers.addElement(new SmsUriHandler(this));
		uriHandlers.addElement(new VideoUriHandler(this));
		
		Thread init = new Thread(new Runnable() {

			public void run() {
				copyFilesFromBundle();
				startRhodesApp();
			}
			
		});
		init.start();
		
		//bAppJustStarted = true;
	}
	
	@Override
	protected void onRestart() {
		super.onRestart();
		Logger.T(TAG, "+++ onRestart");
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Logger.T(TAG, "+++ onStart");
		if (needGeoLocationRestart) {
			GeoLocation.isKnownPosition();
			needGeoLocationRestart = false;
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Logger.T(TAG, "+++ onResume");
		//if (!bAppJustStarted && mainView != null) {
		//	int idx = mainView.activeTab();
		//	mainView.reload(idx);
		//}
	}
	
	@Override
	protected void onPause() {
		Logger.T(TAG, "+++ onPause");
		//int idx = mainView.activeTab();
		//RhoConf.setInt("rho_current_tab", idx);
		//String url = getCurrentUrl();
		//RhoConf.setString("rho_last_visited_url", url);
		//bAppJustStarted = false;
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		Logger.T(TAG, "+++ onStop");
		needGeoLocationRestart = GeoLocation.isAvailable();
		GeoLocation.stop();
		super.onStop();
	}
		
	@Override
	protected void onDestroy() {
		Logger.T(TAG, "+++ onDestroy");
		stopSelf();
		
		super.onDestroy();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		Logger.T(TAG, "+++ onConfigurationChanged");
		super.onConfigurationChanged(newConfig);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			mainView.back(mainView.activeTab());
			return true;
		case KeyEvent.KEYCODE_HOME:
			stopSelf();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		appMenu = new RhoMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (appMenu == null)
			return false;
		return appMenu.onMenuItemSelected(item);
	}
	
	public static void showAboutDialog() {
		performOnUiThread(new Runnable() {
			public void run() {
				final AboutDialog aboutDialog = new AboutDialog(RhodesInstance.getInstance());
				aboutDialog.setTitle("About");
				aboutDialog.setCanceledOnTouchOutside(true);
				aboutDialog.setCancelable(true);
				aboutDialog.show();
			}
		}, false);
	}
	
	public static void showLogView() {
		performOnUiThread(new Runnable() {
			public void run() {
				final LogViewDialog logViewDialog = new LogViewDialog(RhodesInstance.getInstance());
				logViewDialog.setTitle("Log View");
				logViewDialog.setCancelable(true);
				logViewDialog.show();
			}
		}, false);
	}
	
	public static void showLogOptions() {
		performOnUiThread(new Runnable() {
			public void run() {
				final LogOptionsDialog logOptionsDialog = new LogOptionsDialog(RhodesInstance.getInstance());
				logOptionsDialog.setTitle("Logging Options");
				logOptionsDialog.setCancelable(true);
				logOptionsDialog.show();
			}
		}, false);
	}
	
	// Called from native code
	public static void deleteFilesInFolder(String folder) {
		String[] children = new File(folder).list();
		for (int i = 0; i != children.length; ++i)
			Utils.deleteRecursively(new File(folder, children[i]));
	}
	
	private static boolean hasNetwork() {
		if (!Capabilities.NETWORK_STATE_ENABLED) {
			Logger.E(TAG, "Capability NETWORK_STATE disabled");
			return false;
		}
		
		Context ctx = RhodesInstance.getInstance();
		ConnectivityManager conn = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (conn == null)
			return false;
		
		NetworkInfo[] info = conn.getAllNetworkInfo();
		if (info == null)
			return false;
		
		for (int i = 0, lim = info.length; i < lim; ++i) {
			if (info[i].getState() == NetworkInfo.State.CONNECTED)
				return true;
		}
		
		return false;
	}
	
	private static String getCurrentLocale() {
		String locale = Locale.getDefault().getLanguage();
		if (locale.length() == 0)
			locale = "en";
		return locale;
	}
	
	private static String getCurrentCountry() {
		String cl = Locale.getDefault().getCountry();
		return cl;
	}
	
	public static int getScreenWidth() {
		return screenWidth;
	}
	
	public static int getScreenHeight() {
		return screenHeight;
	}
	
	public static Object getProperty(String name) {
		if (name.equalsIgnoreCase("platform"))
			return "ANDROID";
		else if (name.equalsIgnoreCase("locale"))
			return getCurrentLocale();
		else if (name.equalsIgnoreCase("country"))
			return getCurrentCountry();
		else if (name.equalsIgnoreCase("screen_width"))
			return new Integer(getScreenWidth());
		else if (name.equalsIgnoreCase("screen_height"))
			return new Integer(getScreenHeight());
		else if (name.equalsIgnoreCase("has_camera"))
			return new Boolean(isCameraAvailable);
		else if (name.equalsIgnoreCase("has_network"))
			return hasNetwork();
		else if (name.equalsIgnoreCase("ppi_x"))
			return new Float(screenPpiX);
		else if (name.equalsIgnoreCase("ppi_y"))
			return new Float(screenPpiY);
		else if (name.equalsIgnoreCase("phone_number")) {
			TelephonyManager manager = (TelephonyManager)RhodesInstance.getInstance().
				getSystemService(Context.TELEPHONY_SERVICE);
			String number = manager.getLine1Number();
			return number;
		}
		else if (name.equalsIgnoreCase("device_name")) {
			return Build.DEVICE;
		}
		else if (name.equalsIgnoreCase("os_version")) {
			return Build.VERSION.RELEASE;
		}
		
		return null;
	}
	
	public static void exit() {
		RhodesInstance.getInstance().stopSelf();
	}
	
	private void stopSelf() {
		//stopRhodesApp();
		Process.killProcess(Process.myPid());
	}
	
	static {
		NativeLibraries.load();
	}
}
