package mobi.omegacentauri.xaspect;

import java.lang.reflect.Field;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;
import android.inputmethodservice.InputMethodService;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Hook implements IXposedHookLoadPackage {
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		XSharedPreferences prefs = new XSharedPreferences(Apps.class.getPackage().getName(), Apps.PREFS);

		double aspect = Apps.parseAspect(prefs.getString(Apps.PREF_APPS+lpparam.packageName, Apps.DEFAULT_ASPECT));

		XposedBridge.log("XAspect for "+lpparam.packageName+" "+aspect);

		if (! Double.isNaN(aspect))
			fixAspect(lpparam, aspect);
	}

	private void fixAspect(final LoadPackageParam lpparam, final double aspect) {
		try {
			final Class decorClass = Class.forName("com.android.internal.policy.impl.PhoneWindow$DecorView", 
					false, lpparam.classLoader);

			findAndHookMethod(decorClass, "onMeasure", int.class, int.class,
					new XC_MethodHook() {
				protected void beforeHookedMethod(final MethodHookParam param)
						throws Throwable {
					int width = MeasureSpec.getSize((Integer)param.args[0]);
					int height = MeasureSpec.getSize((Integer)param.args[1]);

					if (width != 0 && height != 0) {
						if (Math.abs((double)width / height - aspect) > 0.01) {
							if (height * aspect <= width) {
								width = (int)(height * aspect);
								param.args[0] = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
							}
							else {
								height = (int)(width / aspect);
								param.args[1] = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
							}
							Field outerField = decorClass.getDeclaredField("this$0");
							outerField.setAccessible(true);
							Window w = (Window)outerField.get(param.thisObject);
							w.setLayout(width, height);
						}
					}
				}
			}
					);


		}
		catch (Exception e) {
			XposedBridge.log("Error hooking: " + e);
		}
	}
}
