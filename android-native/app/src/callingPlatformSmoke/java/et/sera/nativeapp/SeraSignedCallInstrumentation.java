package et.sera.nativeapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.content.Intent;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;

/** Only Android framework and Java APIs: no shared Kotlin/AndroidX test runtime. */
public final class SeraSignedCallInstrumentation extends Instrumentation {
    private Bundle options;
    @Override public void onCreate(Bundle arguments) {
        options = arguments;
        super.onCreate(arguments);
        start();
    }

    private boolean hasPost(AccessibilityNodeInfo node) {
        if(node==null)return false;
        String id=node.getViewIdResourceName();
        if(id!=null&&id.startsWith("sera_feed_post_")&&node.isVisibleToUser())return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){try{if(hasPost(child))return true;}finally{child.recycle();}}}
        return false;
    }
    private void verifyFeed(Context target,String refresh) throws Exception {
        Class<?> session=target.getClassLoader().loadClass("et.sera.nativeapp.SessionStore");
        Object store=session.getConstructor(Context.class).newInstance(target);
        session.getMethod("saveRefreshToken",String.class).invoke(store,refresh);
        try {
            Intent launch=new Intent(Intent.ACTION_MAIN).setClassName(target.getPackageName(),"et.sera.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivitySync(launch);
            android.app.UiAutomation ui=getUiAutomation();
            AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;ui.setServiceInfo(info);
            long deadline=SystemClock.elapsedRealtime()+75000;
            while(SystemClock.elapsedRealtime()<deadline){
                AccessibilityNodeInfo root=ui.getRootInActiveWindow();
                if(root!=null){try{if(hasPost(root))return;}finally{root.recycle();}}
                SystemClock.sleep(500);
            }
            throw new AssertionError("Authenticated signed Android home did not render a real post within 75 seconds");
        } finally {session.getMethod("clear").invoke(store);}
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            Class<?> engine = target.getClassLoader().loadClass("et.sera.nativeapp.SeraNativeRtc");
            Executor main = command -> runOnMainSync(command);
            engine.getMethod("verifySignedEngine", Context.class, Executor.class)
                .invoke(null, target, main);
            if (options != null && "true".equals(options.getString("relay_qa"))) {
                engine.getMethod("verifyRelayRecovery", Context.class, Executor.class, String.class, String.class, String.class)
                    .invoke(null, target, main, options.getString("turn_url"), options.getString("turn_username"), options.getString("turn_credential"));
                result.putString("sera_relay_recovery", "passed");
            }
            if(options!=null&&options.getString("feed_refresh")!=null){
                verifyFeed(target,options.getString("feed_refresh"));
                result.putString("sera_feed_smoke","passed");
            }
            result.putString("sera_call_smoke", "passed");
            result.putString("stream", "Signed WebRTC audio offers created twice successfully.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null
                ? failure.getCause() : failure;
            android.util.Log.e("SeraRtc", "Signed call engine smoke failed", cause);
            result.putString("sera_call_smoke", "failed");
            result.putString("stream", android.util.Log.getStackTraceString(cause));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
