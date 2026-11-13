package et.sera.nativeapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;

/** Only Android framework and Java APIs: no shared Kotlin/AndroidX test runtime. */
public final class SeraSignedCallInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            Class<?> engine = target.getClassLoader().loadClass("et.sera.nativeapp.SeraNativeRtc");
            Executor main = command -> runOnMainSync(command);
            engine.getMethod("verifySignedEngine", Context.class, Executor.class)
                .invoke(null, target, main);
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
