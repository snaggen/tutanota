package de.tutao.tutanota;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.jdeferred.Deferred;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.tutao.tutanota.push.PushNotificationService;
import de.tutao.tutanota.push.SseStorage;

/**
 * Created by mpfau on 4/8/17.
 */
public final class Native {
    private static final String JS_NAME = "nativeApp";
    private final static String TAG = "Native";

    private static int requestId = 0;
    private final AndroidKeyStoreFacade keyStoreFacade;
    private Crypto crypto;
    private FileUtil files;
    private Contact contact;
    private SseStorage sseStorage;
    private Map<String, DeferredObject<JSONObject, Exception, ?>> queue = new HashMap<>();
    private final MainActivity activity;
    private volatile DeferredObject<Void, Void, Void> webAppInitialized = new DeferredObject<>();


    Native(MainActivity activity) {
        this.activity = activity;
        crypto = new Crypto(activity);
        contact = new Contact(activity);
        files = new FileUtil(activity);
        sseStorage = new SseStorage(activity);
        keyStoreFacade = new AndroidKeyStoreFacade(activity);
    }

    public void setup() {
        activity.getWebView().addJavascriptInterface(this, JS_NAME);
    }

    /**
     * Invokes method with args. The returned response is a JSON of the following format:
     *
     * @param msg A request (see WorkerProtocol)
     * @return A promise that resolves to a response or requestError (see WorkerProtocol)
     * @throws JSONException
     */
    @JavascriptInterface
    public void invoke(final String msg) {
        new Thread(() -> {
            try {
                final JSONObject request = new JSONObject(msg);
                if (request.get("type").equals("response")) {
                    DeferredObject promise = queue.remove(request.get("id"));
                    promise.resolve(request);
                } else {
                    invokeMethod(request.getString("type"), request.getJSONArray("args"))
                            .then(result -> {
                                sendResponse(request, result);
                            })
                            .fail((FailCallback<Exception>) e -> sendErrorResponse(request, e));
                }
            } catch (JSONException e) {
                Log.e("Native", "could not parse msg:" + msg, e);
            }
        }).start();
    }

    public Promise<JSONObject, Exception, ?> sendRequest(JsRequest type, Object[] args) {
        JSONObject request = new JSONObject();
        String requestId = _createRequestId();
        try {
            JSONArray arguments = new JSONArray();
            for (Object arg : args) {
                arguments.put(arg);
            }
            request.put("id", requestId);
            request.put("type", type.toString());
            request.put("args", arguments);
            this.postMessage(request);
            DeferredObject d = new DeferredObject();
            this.queue.put(requestId, d);
            return d.promise();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    static String _createRequestId() {
        return "app" + requestId++;
    }

    private void sendResponse(JSONObject request, Object value) {
        JSONObject response = new JSONObject();
        try {
            response.put("id", request.getString("id"));
            response.put("type", "response");
            response.put("value", value);
            postMessage(response);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendErrorResponse(JSONObject request, Exception ex) {
        JSONObject response = new JSONObject();
        try {
            response.put("id", request.getString("id"));
            response.put("type", "requestError");
            response.put("error", errorToObject(ex));
            postMessage(response);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void postMessage(final JSONObject json) {
        evaluateJs("tutao.nativeApp.handleMessageFromNative('" + escape(json.toString()) + "')");
    }

    private void evaluateJs(final String js) {
        activity.getWebView().post(() -> {
            activity.getWebView().evaluateJavascript(js, value -> {
                // no response expected
            });
        });
    }

    private Promise invokeMethod(String method, JSONArray args) {
        Deferred<Object, Exception, Void> promise = new DeferredObject<>();
        try {
            switch (method) {
                case "init":
                    if (!webAppInitialized.isResolved()) {
                        webAppInitialized.resolve(null);
                    }
                    promise.resolve("android");
                    break;
                case "reload":
                    webAppInitialized = new DeferredObject<>();
                    activity.loadMainPage(args.getString(0));
                    break;
                case "initPushNotifications":
                    return initPushNotifications();
                case "generateRsaKey":
                    promise.resolve(crypto.generateRsaKey(Utils.base64ToBytes(args.getString(0))));
                    break;
                case "rsaEncrypt":
                    promise.resolve(crypto.rsaEncrypt(args.getJSONObject(0), Utils.base64ToBytes(args.getString(1)), Utils.base64ToBytes(args.getString(2))));
                    break;
                case "rsaDecrypt":
                    promise.resolve(crypto.rsaDecrypt(args.getJSONObject(0), Utils.base64ToBytes(args.getString(1))));
                    break;
                case "aesEncryptFile":
                    Crypto.EncryptedFileInfo efi = crypto.aesEncryptFile(Utils.base64ToBytes(args.getString(0)), args.getString(1), Utils.base64ToBytes(args.getString(2)));
                    promise.resolve(efi.toJSON());
                    break;
                case "aesDecryptFile": {
                    final byte[] key = Utils.base64ToBytes(args.getString(0));
                    final String fileUrl = args.getString(1);

                    promise.resolve(crypto.aesDecryptFile(key, fileUrl));
                    break;
                }
                case "open":
                    return files.openFile(args.getString(0), args.getString(1));
                case "openFileChooser":
                    return files.openFileChooser();
                case "deleteFile":
                    files.delete(args.getString(0));
                    promise.resolve(null);
                    break;
                case "getName":
                    promise.resolve(files.getName(args.getString(0)));
                    break;
                case "getMimeType":
                    promise.resolve(files.getMimeType(Uri.parse(args.getString(0))));
                    break;
                case "getSize":
                    promise.resolve(files.getSize(args.getString(0)) + "");
                    break;
                case "upload":
                    promise.resolve(files.upload(args.getString(0), args.getString(1), args.getJSONObject(2)));
                    break;
                case "download":
                    promise.resolve(files.download(args.getString(0), args.getString(1), args.getJSONObject(2)));
                    break;
                case "clearFileData":
                    files.clearFileData();
                    promise.resolve(null);
                    break;
                case "findSuggestions":
                    return contact.findSuggestions(args.getString(0));
                case "openLink":
                    promise.resolve(openLink(args.getString(0)));
                    break;
                case "getPushIdentifier":
                    promise.resolve(sseStorage.getPushIdentifier());
                    break;
                case "storePushIdentifierLocally":
                    sseStorage.storePushIdentifier(args.getString(0), args.getString(1),
                            args.getString(2));
                    String pushIdentifierId = args.getString(3);
                    String pushIdentifierSessionKeyB64 = args.getString(4);

                    Map<String, String> keys = sseStorage.getPushIdentifierKeys();
                    if (!keys.containsKey(pushIdentifierId)) {
                        String deviceEncSessionKey = this.keyStoreFacade.encryptKey(Utils.base64ToBytes(pushIdentifierSessionKeyB64));
                        keys.put(pushIdentifierId, deviceEncSessionKey);
                        sseStorage.storePushEncSessionKeys(keys);
                    }
                    promise.resolve(true);
                    break;
                case "closePushNotifications":
                    JSONArray addressesArray = args.getJSONArray(0);
                    cancelNotifications(addressesArray);
                    promise.resolve(true);
                    break;
                case "readFile":
                    promise.resolve(Utils.bytesToBase64(
                            Utils.readFile(new File(activity.getFilesDir(), args.getString(0)))));
                    break;
                case "writeFile": {
                    final String filename = args.getString(0);
                    final String contentInBase64 = args.getString(1);
                    Utils.writeFile(new File(activity.getFilesDir(), filename),
                            Utils.base64ToBytes(contentInBase64));
                    promise.resolve(true);
                    break;
                }
                case "changeTheme":
                    activity.changeTheme(args.getString(0));
                    break;
                case "saveBlob":
                    return files.saveBlob(args.getString(0), args.getString(1));
                case "putFileIntoDownloads":
                    final String path = args.getString(0);
                    return files.putToDownloadFolder(path);
                case "getDeviceLog":
                    return new DeferredObject<String, Object, Void>()
                            .resolve(LogReader.getLogFile(activity).toString());
                default:
                    throw new Exception("unsupported method: " + method);
            }
        } catch (Exception e) {
            Log.e(TAG, "failed invocation", e);
            promise.reject(e);
        }
        return promise.promise();
    }


    private void cancelNotifications(JSONArray addressesArray) throws JSONException {
        NotificationManager notificationManager =
                (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
        Objects.requireNonNull(notificationManager);

        ArrayList<String> emailAddesses = new ArrayList<>(addressesArray.length());
        for (int i = 0; i < addressesArray.length(); i++) {
            notificationManager.cancel(Math.abs(addressesArray.getString(i).hashCode()));
            emailAddesses.add(addressesArray.getString(i));
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Before N summary consumes individual notificaitons and we can only cancel the whole
            // group.
            notificationManager.cancel(PushNotificationService.SUMMARY_NOTIFICATION_ID);
        }
        activity.startService(PushNotificationService.notificationDismissedIntent(activity,
                emailAddesses, "Native", false));
    }

    private boolean openLink(@Nullable String uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        PackageManager pm = activity.getPackageManager();
        boolean resolved = intent.resolveActivity(pm) != null;
        if (resolved) {
            activity.startActivity(intent);
        }
        return resolved;
    }

    private Promise<JSONObject, Exception, ?> initPushNotifications() {
        activity.runOnUiThread(() -> {
            activity.askBatteryOptinmizationsIfNeeded();
            activity.setupPushNotifications();
        });
        return new DeferredObject().resolve(null);
    }

    private static JSONObject errorToObject(Exception e) throws JSONException {
        JSONObject error = new JSONObject();
        String errorType = e.getClass().getName();
        error.put("name", errorType);
        error.put("message", e.getMessage());
        error.put("stack", getStack(e));
        return error;
    }

    private static String getStack(Exception e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        String stack = errors.toString();
        return stack;
    }

    private static String escape(String s) {
        return Utils.bytesToBase64(s.getBytes());
    }

    public DeferredObject<Void, Void, Void> getWebAppInitialized() {
        return webAppInitialized;
    }

}


