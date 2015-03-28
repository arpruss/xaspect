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
import android.app.AlertDialog.Builder;
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
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


public class GetApps extends AsyncTask<Void, Integer, List<MyApplicationInfo>> {
	public static final String[] aspects = {
		Apps.DEFAULT_ASPECT,
		//"5:4",
		"4:3",
		//"1.375:1",
		//"1.41:1",
		//"1.43:1",
		"3:2",
		"16:10",
		//"5:3",
		"16:9",
		"1.85:1",
		"2.39:1",
		Apps.CUSTOM
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
				final MyApplicationInfo app = (MyApplicationInfo) listView.getAdapter().getItem(position);
				
				final String prefKey = Apps.PREF_APPS+app.getPackageName();
				final String aspectValue = pref.getString(prefKey, Apps.DEFAULT_ASPECT);

				AlertDialog.Builder b = new AlertDialog.Builder(context);
				int cur = -1;
				for (int i = 0; i < aspects.length ; i++) {
					if (aspects[i].startsWith(Apps.CUSTOM)) {
						if (cur < 0) {
							cur = i;
							aspects[i] = Apps.CUSTOM + " ["+aspectValue+"]";
						}
						else {
							aspects[i] = Apps.CUSTOM;
						}
					}
					
					if (aspectValue.equals(aspects[i])) {
						cur = i;
					}
				}

				b.setSingleChoiceItems(aspects, cur, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						boolean changed = false;
						
						if (! aspects[which].startsWith(Apps.CUSTOM)) {
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
							if (changed) {
								updateApp(listView, app.packageName);
							}
						}
						else {
							setCustom(prefKey, app.packageName, aspectValue);
						}

						dialog.dismiss();
					}

				});
				b.setCancelable(true);
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
	}

	private void setCustom(final String prefKey, final String packageName, String oldAspect) {
		AlertDialog.Builder b = new AlertDialog.Builder(context);
		
		b.setTitle("Set custom aspect ratio");
		b.setMessage("Enter aspect ratio in x:y format (e.g., 1:1.33 or 5:4)");
		final EditText aspectField = new EditText(context);
		if (Double.isNaN(Apps.parseAspect(oldAspect)))
			aspectField.setText("4:3");
		else
			aspectField.setText(oldAspect);
		aspectField.setSingleLine();
		b.setView(aspectField);
		b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (Double.isNaN(Apps.parseAspect(aspectField.getText().toString()))) {
					Toast.makeText(context, "Invalid aspect value", Toast.LENGTH_LONG).show();
				}
				else {
					pref.edit().putString(prefKey, aspectField.getText().toString().trim()).commit();
					updateApp(listView, packageName);
					dialog.dismiss();
				}
			}
		});
		b.setCancelable(true);
		b.show();
	}


	private void updateApp(ListView listView, String packageName) {
		listView.invalidateViews();
		if (! packageName.equals("android")) {
			ActivityManager am = (ActivityManager)context.getSystemService(Activity.ACTIVITY_SERVICE);
			for (RunningAppProcessInfo r : am.getRunningAppProcesses()) {
				if (r.pid != 0 && r.processName.equals(packageName)) {
					kill(r.pid);
					break;
				}
			}
		}
	}

	private void kill(final int pid) {
		new Thread() {
			@Override
			public void run() {
				ProcessBuilder pb = new ProcessBuilder("su", "-c", "kill", "-9", ""+pid);
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
