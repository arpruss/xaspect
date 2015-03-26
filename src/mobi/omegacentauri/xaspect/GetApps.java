package mobi.omegacentauri.xaspect;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

import android.widget.CheckBox;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;


import mobi.omegacentauri.xaspect.R;
import mobi.omegacentauri.xaspect.Apps;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;


public class GetApps extends AsyncTask<Void, Integer, List<MyApplicationInfo>> {
	public static final String[] aspects = {
		Apps.DEFAULT_ASPECT,
		"5:4",
		"4:3",
		"1.375:1",
		"1.41:1",
		"1.43:1",
		"3:2",
		"16:10",
		"5:3",
		"16:9",
		"1.85:1",
		"2.39:1",
	};
	final PackageManager pm;
	final Context	 context;
	final ListView listView;
	final SharedPreferences pref;
	public final static String cachePath = "app_labels"; 
	ProgressDialog progress;
	
	GetApps(Context c, ListView lv, SharedPreferences pref) {
		context = c;
		this.pref = pref;
		pm = context.getPackageManager();
		listView = lv;
	}

	private boolean profilable(ApplicationInfo a) {
		return true;
	}

	@Override
	protected List<MyApplicationInfo> doInBackground(Void... c) {
		Log.v("getting", "installed");
		
		Intent launchIntent = new Intent(Intent.ACTION_MAIN);
		launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		
		List<ApplicationInfo> list = 
				pm.getInstalledApplications(0);

		List<MyApplicationInfo> myList = new ArrayList<MyApplicationInfo>();
		
		MyCache cache = new MyCache(MyCache.genFilename(context, cachePath));
		
		for (int i = 0 ; i < list.size() ; i++) {
			publishProgress(i, list.size());
			MyApplicationInfo myAppInfo;
			myAppInfo = new MyApplicationInfo(cache, pm, list.get(i));
			if (!myList.contains(myAppInfo))
				myList.add(myAppInfo);
		}
		cache.commit();
		
		publishProgress(list.size(), list.size());
		
		return myList;
	}
	
	@Override
	protected void onPreExecute() {
//		listView.setVisibility(View.GONE);
		progress = new ProgressDialog(context);
		progress.setCancelable(false);
		progress.setMessage("Getting applications...");
		progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progress.setIndeterminate(true);
		progress.show();
	}
	
	protected void onProgressUpdate(Integer... p) {
		progress.setIndeterminate(false);
		progress.setMax(p[1]);
		progress.setProgress(p[0]);
	}
	
	@Override
	protected void onPostExecute(final List<MyApplicationInfo> appInfo) {
		
		ArrayAdapter<MyApplicationInfo> appInfoAdapter = 
			new ArrayAdapter<MyApplicationInfo>(context, 
					R.layout.twoline, 
					appInfo) {

			public View getView(int position, View convertView, ViewGroup parent) {
				View v;				
				
				if (convertView == null) {
	                v = View.inflate(context, R.layout.twoline, null);
	            }
				else {
					v = convertView;
				}

				final MyApplicationInfo a = appInfo.get(position); 
				TextView tv = (TextView)v.findViewById(R.id.text1);
				tv.setText(a.getLabel());
				tv = (TextView)v.findViewById(R.id.text2);
				tv.setText(a.getPackageName());
				tv = (TextView)v.findViewById(R.id.aspect);
				String aspectValue = pref.getString(Apps.PREF_APPS+a.getPackageName(), Apps.DEFAULT_ASPECT);
				tv.setText(aspectValue);
				return v;
			}				
		};
		
		appInfoAdapter.sort(MyApplicationInfo.LabelComparator);
		listView.setAdapter(appInfoAdapter);
		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Log.v("XAspect", "onItemSelected "+position);
				final MyApplicationInfo app = (MyApplicationInfo) listView.getAdapter().getItem(position);
				
				final String prefKey = Apps.PREF_APPS+app.getPackageName();
				final String aspectValue = pref.getString(prefKey, Apps.DEFAULT_ASPECT);

				AlertDialog.Builder b = new AlertDialog.Builder(context);
				int cur = -1;
				for (int i = 0; i < aspects.length ; i++) {
					
					if (cur < 0 && aspects[i].equals(Apps.CUSTOM))
						cur = i;
					
					if (aspectValue.equals(aspects[i])) {
						cur = i;
						break;
					}
				}

				b.setSingleChoiceItems(aspects, cur, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						boolean changed = false;
						
						if (aspects[which] != Apps.CUSTOM) {
							if (aspects[which] == Apps.DEFAULT_ASPECT) {
								if (! aspectValue.equals(Apps.DEFAULT_ASPECT)) {
									pref.edit().remove(prefKey).commit();
									changed = true;
								}
							}
							else {
								if (! aspectValue.equals(aspects[which])) {
									pref.edit().putString(prefKey, aspects[which]).commit();
									changed = true;
								}
							}
						}
						else {
							//TODO: custom
						}
						if (changed) {
							listView.invalidateViews();
							if (! app.packageName.equals("android")) {
								ActivityManager am = (ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE);
								for (RunningAppProcessInfo r : am.getRunningAppProcesses()) {
									if (r.processName.equals(app.packageName)) {
										Log.v("XAspect", "Running "+app.packageName);
										kill(app.packageName);
										break;
									}
								}
							}
						}

						dialog.dismiss();
					}

				});
				b.show();
			}

		});
		
		Map<String,?> map = pref.getAll();
		
		for (MyApplicationInfo app:appInfo) {
			if (map.containsKey(Apps.PREF_APPS + app.getPackageName())) {
				map.remove(Apps.PREF_APPS + app.getPackageName());
			}
		}
		
		if (map.size()>0) {
			SharedPreferences.Editor ed = pref.edit();
			for (String s: map.keySet()) {
				if (s.startsWith(Apps.PREF_APPS))  
					ed.remove(s);
			}
			ed.commit();
		}
		
		try {
			progress.dismiss();
		}
		catch (Exception e) {
		}
//		listView.setVisibility(View.VISIBLE);
	}

	private void kill(final String packageName) {
		new Thread() {
			@Override
			public void run() {
				ProcessBuilder pb = new ProcessBuilder("su", "-c", "killall", "-9", packageName);
				pb.redirectErrorStream(true);
				try {
					Process p = pb.start();
					InputStream in = p.getInputStream();
					while (-1 != in.read());
					in.close();
					p.waitFor();
				} catch (IOException e) {
				} catch (InterruptedException e) {
				}
				
			}
		}.start();
	}
}
