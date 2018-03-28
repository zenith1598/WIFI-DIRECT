package com.codingblocks.xender;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;


/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
// used for sending the file to some other client.
// while for recieving , it has already been dealt in  "DeviceDetailFragment" using asynktask.
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000; // milli seconds;
    public static final String ACTION_SEND_FILE = "com.example.android.wifidirect.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";


    public FileTransferService(String name) {
        super(name);
    }


    public FileTransferService() {
        super("FileTransferService");
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {

            String fileuri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            Socket socket = new Socket();
            int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);


            Log.d(XenderActivity.TAG, "Opening client socket - ");
            try {

                socket.bind(null);
                socket.connect(new InetSocketAddress(host, port));
                Log.d(XenderActivity.TAG, "Client socket - " + socket.isConnected());

                OutputStream stream = socket.getOutputStream();

                ContentResolver cr = context.getContentResolver();
                InputStream is = null;

                try {
                    is = cr.openInputStream(Uri.parse(fileuri));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                 DeviceDetailFragment.copyFile(is, stream);
                Log.d(XenderActivity.TAG, "Client: Data written");


            } catch (IOException e) {
                Log.e(XenderActivity.TAG, e.getMessage());

            } finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }


}
