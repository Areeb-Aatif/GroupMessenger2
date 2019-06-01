package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static ArrayList<String> ports = null;
    static final int SERVER_PORT = 10000;
    private Uri uri;
    ReentrantLock lock = new ReentrantLock();
    HashMap<Long, String> buffer = new LinkedHashMap<Long, String>();
    HashMap<Long, String> temp = new LinkedHashMap<Long, String>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        uri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        ports = new ArrayList<String>();
        ports.add(REMOTE_PORT0);
        ports.add(REMOTE_PORT1);
        ports.add(REMOTE_PORT2);
        ports.add(REMOTE_PORT3);
        ports.add(REMOTE_PORT4);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button sendButton = (Button) findViewById(R.id.button4);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView textView = (TextView) findViewById(R.id.textView1);
                textView.setMovementMethod(new ScrollingMovementMethod());
                textView.append("\t" + msg); // This is one way to display a string.

                StringBuffer sendPorts = new StringBuffer();
                for(String p: ports){
                    sendPorts.append(p);
                    sendPorts.append("-");
                }

                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, sendPorts.toString());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    /** Server Class  **/
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            try {
                while(true){
                    Socket socket = serverSocket.accept();
                    BufferedReader bReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String message = bReader.readLine();
                    BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    bWriter.write("Message Received by Server");
                    bWriter.flush();
                    socket.close();

                    String msg[] = message.split("-");

                    String messageReceived = msg[0];
                    Long time = Long.parseLong(msg[1]);

                    publishProgress(messageReceived);

                    lock.lock();
                    if(buffer.isEmpty()){
                        Log.d("ServerMsg", "bye");
                        ContentValues cv = new ContentValues();
                        cv.put("key", 0);
                        cv.put("value", messageReceived);
                        getContentResolver().insert(uri, cv);
                        buffer.put(time, messageReceived);
                    }else{
                        int index=0;
                        for(Map.Entry<Long, String> entry: buffer.entrySet()){
                            if(entry.getKey() < time){
                                temp.put(entry.getKey(), entry.getValue());
                                index++;
                            }else{
                                break;
                            }
                        }
                        temp.put(time, messageReceived);
                        ContentValues cv = new ContentValues();
                        cv.put("key", index);
                        cv.put("value", messageReceived);
                        getContentResolver().insert(uri, cv);
                        index++;
                        for(Map.Entry<Long, String> entry: buffer.entrySet()){
                            if(entry.getKey() > time){
                                temp.put(entry.getKey(), entry.getValue());
                                cv = new ContentValues();
                                cv.put("key", index);
                                cv.put("value", entry.getValue());
                                getContentResolver().insert(uri, cv);
                                index++;
                            }
                        }
                        buffer = temp;
                        temp = new LinkedHashMap<Long, String>();
                    }
                    lock.unlock();
                }
            } catch (IOException e) {
                Log.e(TAG, "Server Socket failed");
            }
            return null;
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView textView = (TextView) findViewById(R.id.textView1);
            textView.append(strReceived + "\t\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                Timestamp ts = new Timestamp(System.currentTimeMillis());
                Long time = ts.getTime();
                StringBuffer msgToSend = new StringBuffer(msgs[0].trim()).append("-").append(time).append("\n");
                String msg[] = msgs[1].split("-");

                for(int i=0; i<msg.length; i++){
                    String remotePort = msg[i];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String response = null;
                    int waitTime = 0;
                    BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    bWriter.write(msgToSend.toString());
                    bWriter.flush();

                    BufferedReader bReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    do {
                        // Waiting for Acknowledgement from Server
                        try{
                            response = bReader.readLine();
                            waitTime++;
                            if(waitTime == 500){
                                ports.remove(remotePort);
                                break;
                            }
                        }catch(IOException io){
                            Log.e(TAG, "ClientTask socket IOException");
                        }

                    }while(response == null);
                    socket.close();
                }

                /*
                 * TODO: Fill in your client code that multi-casts a message.
                 */
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }
}
