package com.eveningoutpost.dexdrip.ShareModels;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.eveningoutpost.dexdrip.ShareModels.Models.ExistingFollower;
import com.eveningoutpost.dexdrip.ShareModels.Models.InvitationPayload;
import com.eveningoutpost.dexdrip.ShareModels.Models.ShareAuthenticationBody;
import com.eveningoutpost.dexdrip.ShareModels.Models.ShareUploadPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okio.Buffer;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Retrofit;

/**
 * Created by stephenblack on 12/26/14.
 */
public class ShareRest {
    public class ShareException extends RuntimeException {
        ShareException(String message) {
            super(message);
        }

        ShareException(String message, Exception e) {
            super(message, e);
        }
    }

    public static String TAG = ShareRest.class.getSimpleName();

    private String sessionId;

    private String username;
    private String password;
    private String serialNumber;
    private DexcomShare dexcomShareApi;

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("dexcom_account_name".equals(key)) {
                username = sharedPreferences.getString(key, null);
            } else if ("dexcom_account_password".equals(key)) {
                password = sharedPreferences.getString(key, null);
            } else if ("share_key".equals(key)) {
                serialNumber = sharedPreferences.getString(key, null);
            }

        }
    };

    private static final String SHARE_BASE_URL = "https://share1.dexcom.com/ShareWebServices/Services/";
    private SharedPreferences sharedPreferences;

    public ShareRest (Context context, OkHttpClient okHttpClient) {
        OkHttpClient httpClient = okHttpClient != null ? okHttpClient : getOkHttpClient();

        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SHARE_BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        dexcomShareApi = retrofit.create(DexcomShare.class);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sessionId = sharedPreferences.getString("dexcom_share_session_id", null);
        username = sharedPreferences.getString("dexcom_account_name", null);
        password = sharedPreferences.getString("dexcom_account_password", null);
        serialNumber = sharedPreferences.getString("share_key", null);
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        if ("".equals(sessionId)) // migrate previous empty sessionIds to null;
            sessionId = null;
        if ("".equals(serialNumber))
            serialNumber = null;
    }

    private OkHttpClient getOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                @Override
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] chain,
                        String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] chain,
                        String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            } };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient okHttpClient = new OkHttpClient();
            okHttpClient.networkInterceptors().add(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    // Add user-agent and relevant headers.
                    Request original = chain.request();
                    Request copy = original.newBuilder().build();
                    Request modifiedRequest = original.newBuilder()
                            .header("User-Agent", "Dexcom Share/3.0.2.11 CFNetwork/711.2.23 Darwin/14.0.0")
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .build();
                    Log.d(TAG, "Sending request: " + modifiedRequest.toString());
                    Buffer buffer = new Buffer();
                    copy.body().writeTo(buffer);
                    Log.d(TAG, "Request body: " + buffer.readUtf8());

                    Response response = chain.proceed(modifiedRequest);
                    Log.d(TAG, "Received response: " + response.toString());
                    if (response.body() != null) {
                        MediaType contentType = response.body().contentType();
                        String bodyString = response.body().string();
                        Log.d(TAG, "Response body: " + bodyString);
                        return response.newBuilder().body(ResponseBody.create(contentType, bodyString)).build();
                    } else
                        return response;
                }
            });

            okHttpClient.setSslSocketFactory(sslSocketFactory);
            okHttpClient.setHostnameVerifier(new HostnameVerifier() {

                @Override
                public boolean verify(String hostname, SSLSession session) {
                        return true;
                }
            });

            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException("Error occurred initializing OkHttp: ", e);
        }
    }

    private String getSessionId() throws ShareException {
        AsyncTask<String, Void, Object> task = new AsyncTask<String, Void, Object>() {

            @Override
            protected Object doInBackground(String... params) {
                try {
                    Boolean isActive = null;
                    Log.d(TAG, "validating session ID");
                    if (params[0] != null)
                        isActive = dexcomShareApi.checkSessionActive(params[0]).execute().body();
                    if (isActive == null || !isActive) {
                        Log.d(TAG, "uppdating auth params");
                        return updateAuthenticationParams();
                    } else
                        return params[0];
                } catch (IOException e) {
                    return new ShareException("Error retrieving Share session id. " +e.getMessage(), e);
                } catch (ShareException e) {
                    return e;
                }
            }

            private String updateAuthenticationParams() throws IOException, ShareException {
                sessionId = dexcomShareApi.getSessionId(new ShareAuthenticationBody(password, username).toMap()).execute().body();
                if (serialNumber == null || serialNumber.equals("")) {
                    throw new ShareException("No receiver serial number specified.");
                }
                if (sessionId == null) {
                    throw new ShareException("Unable to retrieve Share session ID.  Check username and password.");
                }
                dexcomShareApi.authenticatePublisherAccount(sessionId, serialNumber, new ShareAuthenticationBody(password, username).toMap()).execute().body();
                dexcomShareApi.StartRemoteMonitoringSession(sessionId, serialNumber).execute();
                String assignment = dexcomShareApi.checkMonitorAssignment(sessionId, serialNumber).execute().body();
                if (!"AssignedToYou".equals(assignment)) {
                    dexcomShareApi.updateMonitorAssignment(sessionId, serialNumber).execute();
                }
                return sessionId;
            }

        };

        if (sessionId == null || sessionId.equals(""))
            try {
                Object result = task.execute(sessionId).get();
                if (result instanceof ShareException) {
                    throw (ShareException) result;
                }
                sessionId = (String) result;
            } catch (InterruptedException | ExecutionException | ShareException e) {
                throw new ShareException("Unable to start Share session.  "+e.getMessage(), e);
            }
        return sessionId;
    }

    public void getContacts(Callback<List<ExistingFollower>> existingFollowerListener) {
        try {
            dexcomShareApi.getContacts(getSessionId()).enqueue(new AuthenticatingCallback<List<ExistingFollower>>(existingFollowerListener) {
                @Override
                public void onRetry() {
                    dexcomShareApi.getContacts(getSessionId()).enqueue(this);
                }
            });
        } catch (ShareException e) {
            existingFollowerListener.onFailure(e);
        }
    }

    public void uploadBGRecords(final ShareUploadPayload bg, Callback<ResponseBody> callback) {
        try {
            dexcomShareApi.uploadBGRecords(getSessionId(), bg).enqueue(new AuthenticatingCallback<ResponseBody>(callback) {
                @Override
                public void onRetry() {
                dexcomShareApi.uploadBGRecords(getSessionId(), bg).enqueue(this);
            }
        });
        } catch (ShareException e) {
            callback.onFailure(e);
        }
    }

    public void createContact(final String followerName, final String followerEmail, Callback<String> callback) {
        try {
            dexcomShareApi.createContact(getSessionId(), followerName, followerEmail).enqueue(new AuthenticatingCallback<String>(callback) {
                @Override
                public void onRetry() {
                    dexcomShareApi.createContact(getSessionId(), followerName, followerEmail).enqueue(this);
                }
            });
        } catch (ShareException e) {
            callback.onFailure(e);
        }
    }

    public void createInvitationForContact(final String contactId, final InvitationPayload invitationPayload, Callback<String> callback) {
        try {
            dexcomShareApi.createInvitationForContact(getSessionId(), contactId, invitationPayload).enqueue(new AuthenticatingCallback<String>(callback) {
                @Override
                public void onRetry() {
                    dexcomShareApi.createInvitationForContact(getSessionId(), contactId, invitationPayload).enqueue(this);
                }
            });
        } catch (ShareException e) {
            callback.onFailure(e);
        }
    }

    public void deleteContact(final String contactId, Callback<ResponseBody> deleteFollowerListener) {
        try {
            dexcomShareApi.deleteContact(getSessionId(), contactId).enqueue(new AuthenticatingCallback<ResponseBody>(deleteFollowerListener) {
                @Override
                public void onRetry() {
                    dexcomShareApi.deleteContact(getSessionId(), contactId).enqueue(this);
                }
            });
        } catch (ShareException e) {
            deleteFollowerListener.onFailure(e);
        }
    }

    private abstract class AuthenticatingCallback<T> implements Callback<T> {

        private int attempts = 0;
        private Callback<T> delegate;
        AuthenticatingCallback(Callback<T> callback) {
            this.delegate = callback;
        }

        public abstract void onRetry();

        @Override
        public void onResponse(retrofit.Response<T> response, Retrofit retrofit) {
            if (response.code() == 500 && attempts == 0) {
                // retry with new session ID
                attempts += 1;
                try {
                    dexcomShareApi.getSessionId(new ShareAuthenticationBody(password, username).toMap()).enqueue(new Callback<String>() {
                        @Override
                        public void onResponse(retrofit.Response<String> response, Retrofit retrofit) {
                            if (response.isSuccess()) {
                                sessionId = response.body();
                                ShareRest.this.sharedPreferences.edit().putString("dexcom_share_session_id", sessionId).apply();
                                onRetry();
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            delegate.onFailure(t);
                        }
                    });
                } catch (ShareException e) {
                    delegate.onFailure(e);
                }
            } else {
                delegate.onResponse(response, retrofit);
            }
        }

        @Override
        public void onFailure(Throwable t) {
            delegate.onFailure(t);
        }
    }
}
