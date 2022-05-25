package module.gradle;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import module.AbuildApplication;
import module.resources.MonkeyPatcher;
import module.AbuildCore;
import module.IDynamic;
import module.util.ActivityManager;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

public class GradleDynamic implements IDynamic {

	private static final String TAG = "Freeline.GradleDynamic";

	private Application app;
	
	public GradleDynamic(Application context) {
		this.app = context;
	}

		@Override
		public boolean applyDynamicRes(HashMap<String, String> dynamicRes) {
			String dynamicResPath = dynamicRes.get(AbuildCore.DEFAULT_PACKAGE_ID);
			String processName = AbuildApplication.getProcessName();

			Log.i(TAG, "dynamicResPath: " + dynamicResPath+"processName"+processName+" ActivityManager.getAllActivities()"+ActivityManager.getAllActivities());
			if (!TextUtils.isEmpty(dynamicResPath)) {
                Application realApplication = AbuildCore.getRealApplication();
				MonkeyPatcher.monkeyPatchApplication(app, app, realApplication, dynamicResPath);
				MonkeyPatcher.monkeyPatchExistingResources(app, dynamicResPath, Arrays.asList(ActivityManager.getAllActivities()));
				Log.i(TAG, "GradleDynamic apply dynamic resource successfully");
			}
		return true;
	}

	@Override
	public String getOriginResPath(String packageId) {
		File baseResFile = new File(AbuildCore.getDynamicInfoTempDir(), "full-res-pack.so");
		return baseResFile.getAbsolutePath();
	}

	@Override
	public void clearResourcesCache() {
		
	}

}
