import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;


@SuppressWarnings("deprecation")
public class TusClient extends IntentService {

	private static final int maxFile = 512 * 1024;
	private static final String PREFS_NAME = "UploadResumable";
	private static final String hostUrl = "http://example.com/ws/archive/upload";

	private int offset;
	private int fileSize;
	
	private String videoTitle;
	private String filePath;
	private String categoryId;
	private String isPublic;
	private String location;
	private String fileName;
	private int NOTIFY_ID = 1;

	NotificationManager mNotifyManager;
	Builder mBuilder;

	HttpPost httppost;
	String uploadURL;
	ProgressBarListener listener;

	public TusClient() {
		super("TusClient");
		offset = 0;

	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		//file metadata. Just an example
		videoTitle = intent.getStringExtra("videoTitle");
		filePath = intent.getStringExtra("filePath");
		categoryId = intent.getStringExtra("categoryId");
		isPublic = intent.getStringExtra("isPublic");
		fileSize = (int) (new File(filePath)).length();
		fileName = getFileName(filePath);
	
		mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder = new NotificationCompat.Builder(TusClient.this);
		mBuilder.setContentTitle("Upload " + fileName).setContentText(filePath)
				.setTicker("Upload video starting")
				.setSmallIcon(R.drawable.ic_launcher);
		mNotifyManager.notify(NOTIFY_ID, mBuilder.build());

		uploadURL = hostUrl;
		location = getLocation(fileName);

		try {

			if (location == null || location == "")
				doPost();

			doHead();
			while (location != null && location != "" && offset < fileSize) {
				doPatch();
				doHead();
			}

			if (offset == fileSize) {
				try {
					onUploadFinished("success");
				} catch (JSONException e) {					
					e.printStackTrace();
				}
			}

		} catch (Exception e) {
			try {
				onUploadFinished("failed");
			} catch (JSONException e2) {
				e2.printStackTrace();
			}
			e.printStackTrace();
		}
	}

	private String getLocation(String fileName) {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		return settings.getString(fileName, null);
	}

	private void setLocation(String fileName, String bufferUuid) {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(fileName, bufferUuid);
		editor.commit();
	}

	private String getFileName(String filePath) {
		String[] s = filePath.split("/");
		return s[s.length - 1];
	}

	private String executeRequest(HttpRequestBase requestBase) {
		String responseString = "";
		InputStream responseStream = null;
		HttpParams httpParameter = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParameter, 30000);
		HttpConnectionParams.setSoTimeout(httpParameter, 50000);
		HttpClient client = new DefaultHttpClient(httpParameter);
		try {
			HttpResponse response = client.execute(requestBase);
			if (response != null) {
				if (response.getStatusLine().getStatusCode() == 201) {
					location = response.getHeaders("Location")[0].getValue();
					responseString = response.getStatusLine().getReasonPhrase();
					
				} else if (response.getStatusLine().getStatusCode() == 200) {
					if (requestBase.getMethod() == "HEAD") {
						offset = Integer.parseInt(response.getHeaders("Offset")[0].getValue());
						responseString = response.getStatusLine().getReasonPhrase();
						
					} else
						responseString = response.getStatusLine().getReasonPhrase();
					
				} else
					throw new IOException(response.getStatusLine().getReasonPhrase());
				
			} else
				throw new IOException("response is null");
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (responseStream != null) {
				try {
					responseStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		client.getConnectionManager().shutdown();

		return responseString;
	}

	private void onUploadFinished(String response) throws JSONException {
		
		if (response.contains("success")) {
			int percentage = (int)(((float) (offset) / (float) (fileSize)) * 100);
			mBuilder.setContentText("Upload Finish").setTicker("Upload Finish " + String.valueOf(percentage) + "%").setProgress(0, 0, false);
			mNotifyManager.notify(NOTIFY_ID, mBuilder.build());

		} else {
			mBuilder.setContentText("Upload Failed").setTicker("Upload Failed").setProgress(0, 0, false);
			mNotifyManager.notify(NOTIFY_ID, mBuilder.build());
		}
	}

	public class ProgressBarListener {
		public ProgressBarListener() {
			super();
		}

		public void updateTransferred(int byteCount, int totalnum) {
			mBuilder.setProgress(totalnum, byteCount, false);
			mNotifyManager.notify(NOTIFY_ID, mBuilder.build());
		}
	}

	private static byte[] doSplit(String filePath, int offset) {
		File file = new File(filePath);
		FileInputStream fis = null;
		int availableSize, bufferSize;
		byte[] buffer = null;

		try {
			fis = new FileInputStream(file);
			fis.skip(offset);
			availableSize = fis.available();
			bufferSize = Math.min(availableSize, maxFile);
			buffer = new byte[bufferSize];
			fis.read(buffer, 0, bufferSize);
			fis.close();

		} catch (FileNotFoundException e) {			
			e.printStackTrace();
		} catch (IOException e) {			
			e.printStackTrace();
		}

		return buffer;
	}

	private void doPost() throws Exception {

		httppost = new HttpPost(uploadURL);
		listener = new ProgressBarListener();
		File myFile = new File(filePath);

		httppost.setHeader("Authorization", "Bearer " + tvbAPI.getToken());
		httppost.setHeader("Entity-Length", String.valueOf(myFile.length()));
		httppost.setHeader("X-File-Extension", FilenameUtils.getExtension(filePath));	

		try {
			MultipartEntityBuilder entity = MultipartEntityBuilder.create();
			entity.addPart("title", new StringBody(videoTitle));
			entity.addPart("category_id", new StringBody(categoryId));
			entity.addPart("is_public", new StringBody(isPublic));

			httppost.setEntity(entity.build());

		} catch (IOException e) {
			e.printStackTrace();
		}

		String response = executeRequest(httppost);
		if (!response.contains("Created"))
			throw new Exception(response);

		setLocation(fileName, location);

	}

	private void doHead() throws Exception {

		HttpHead httpHead = new HttpHead(location);
		String response = executeRequest(httpHead);
		
		if (response.contains("OK")) {
			int percentage = (int)(((float) (offset) / (float) (fileSize)) * 100);
			mBuilder.setContentText("Upload Progress " +String.valueOf(percentage) + "%").setProgress(fileSize, offset, false);
			mNotifyManager.notify(NOTIFY_ID, mBuilder.build());
		} else
			throw new Exception(response);

	}

	private void doPatch() throws Exception {

		HttpPatch httpPatch = new HttpPatch(location);
		httpPatch.setHeader("Content-Type", "application/offset+octet-stream");
		httpPatch.setHeader("Offset", String.valueOf(offset));

		ByteArrayEntity entity = new ByteArrayEntity(doSplit(filePath, offset));
		httpPatch.setEntity(entity);

		String response = executeRequest(httpPatch);
		if (!response.contains("OK")) 
			throw new Exception(response);			
	}
		

}
