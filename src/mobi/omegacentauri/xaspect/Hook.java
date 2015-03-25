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

		double aspect = Apps.parseAspect(prefs.getString(Apps.PREF_APPS+lpparam.packageName, null));
		
		if (! Double.isNaN(aspect))
			fixAspect(lpparam, aspect);
	}

	private void fixAspect(final LoadPackageParam lpparam, final double aspect) {

		//		XposedBridge.hookAllConstructors(Window.class,
		//				new XC_MethodHook() {
		//					@Override
		//					protected void afterHookedMethod(final MethodHookParam param)
		//							throws Throwable {
		//						Window w = (Window)param.thisObject;
		//						Field attr = Window.class.getDeclaredField("mWindowAttributes");
		//						attr.setAccessible(true);
		//						WindowManager.LayoutParams lp = (LayoutParams) attr.get(w);
		//						XposedBridge.log("Window "+lp.width+" "+lp.height);
		//						w.setLayout(1256, 942); //LayoutParams.MATCH_PARENT);							
		//					}
		//				});

		//		findAndHookMethod("android.content.res.Resources", lpparam.classLoader,
		//				"getDisplayMetrics", new XC_MethodHook() {
		//			@Override
		//			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
		//				DisplayMetrics metrics = (DisplayMetrics)param.getResult();
		//				XposedBridge.log("Metrics "+metrics.widthPixels+" "+metrics.heightPixels);
		//				metrics.widthPixels = 1024;
		//				metrics.heightPixels = 768;
		//			}
		//		});

		try {
			final Class phoneWindowClass = Class.forName("com.android.internal.policy.impl.PhoneWindow", 
					false, lpparam.classLoader);


			XposedHelpers.findAndHookConstructor(phoneWindowClass, Context.class,
					new XC_MethodHook() {
				@SuppressLint("NewApi")
				@Override
				protected void afterHookedMethod(final MethodHookParam param)
						throws Throwable {
					XposedBridge.log("creating phonewindow");
					Context context = (Context)param.args[0];
					WindowManager tmpWM = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
					if (tmpWM != null) {
						wm = tmpWM;
					}

					if (wm != null) {
						Point size = new Point();
						Display display = wm.getDefaultDisplay();
						DisplayMetrics m = new DisplayMetrics();
						display.getMetrics(m);
						XposedBridge.log("display "+m.widthPixels+" "+m.heightPixels);
						int width = display.getWidth();
						int height = display.getHeight();
						//										height = 1200;
						XposedBridge.log("creating phonewindow "+width+" "+height);
						if (width != 0 && height != 0) {
							if (Math.abs((double)width / height - aspect) > 0.01) {
								if (height * aspect <= width) {
//									((Window)param.thisObject).setLayout((int)(height * aspect), height);
								}
								else {
//									((Window)param.thisObject).setLayout(width, (int)(width / aspect));													
								}
							}
						}
					}
				}
			});

			final Class decorClass = Class.forName("com.android.internal.policy.impl.PhoneWindow$DecorView", 
					false, lpparam.classLoader);

			findAndHookMethod("android.widget.FrameLayout", lpparam.classLoader, "onMeasure", int.class, int.class,
					new XC_MethodHook() {
				protected void beforeHookedMethod(final MethodHookParam param)
						throws Throwable {
					FrameLayout f = (FrameLayout)param.thisObject;
					if (f.getClass() == decorClass) {
						int width = MeasureSpec.getSize((Integer)param.args[0]);
						int height = MeasureSpec.getSize((Integer)param.args[1]);

						if (width != 0 && height != 0) {
							XposedBridge.log(f.getClass().toString()+" "+width+" "+height);
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
			}
					);

			findAndHookMethod("com.android.internal.policy.impl.PhoneWindow", lpparam.classLoader,
					"generateLayout", decorClass, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					XposedBridge.log("generateLayout");
				}
			});

			findAndHookMethod(decorClass,
					"setFrame", int.class, int.class, int.class, int.class, new XC_MethodHook() {

				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					//					param.args[2] = 1024;
					//					param.args[3] = 768;
				}

				@Override
				protected void afterHookedMethod(MethodHookParam param) throws Throwable {
					XposedBridge.log("setFrame "+(Integer)param.args[2]+" "+(Integer)param.args[3]+" ");
					if ((Boolean)param.getResult()) {
						Field outerField = decorClass.getDeclaredField("this$0");
						outerField.setAccessible(true);
						Window w = (Window)outerField.get(param.thisObject);
						//						WindowManager.LayoutParams lp = w.getAttributes();
						//						lp.width = 1024;
						//						lp.height = 768;
						//						w.setAttributes(lp);
						//						w.setLayout(1024, 768);
						XposedBridge.log("setting 1024x768");
					}
					//					ViewGroup.LayoutParams lp = g.getLayoutParams();
					//					lp.width = 1024;
					//					lp.height = 768;
					//					g.setLayoutParams(lp);
					//					XposedBridge.log("dims "+g.getWidth()+" "+g.getLayoutParams().height);
				}
			});
			XposedBridge.log("Hooked aspect");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}
