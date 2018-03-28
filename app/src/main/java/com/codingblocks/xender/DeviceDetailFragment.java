package com.codingblocks.xender;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import android.app.Fragment;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;


public class DeviceDetailFragment extends Fragment implements WifiP2pManager.ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    private View contentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    ProgressDialog progressDialog = null;


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.fragment_device_detail, null);

        contentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(), "Press Back TO Cancel", "Connecting to" + device.deviceAddress, true, true, new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        ((DeviceListFragment.DeviceActionListener) getActivity()).cancelDisconnect();

                    }
                });
                ((DeviceListFragment.DeviceActionListener) getActivity()).connect(config);


            }
        });


        contentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        ((DeviceListFragment.DeviceActionListener) getActivity()).disconnect();
                    }
                });


        contentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.setType("image/*|application/pdf|audio/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });

        return contentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        // User has picked an image. Transfer it to group owner i.e peer using
        // FileTransferService.
        if (data != null) {
            // Now the user may choose multiple images
            ClipData clipdata = data.getClipData();
            if (clipdata == null) {
                // this implies that only one imagehas been choosen

                Uri uri = data.getData();
                TextView statusText = (TextView) contentView.findViewById(R.id.status_text);
                statusText.setText("Sending: " + uri);
                Log.d(XenderActivity.TAG, "Intent----------- " + uri);
                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                        info.groupOwnerAddress.getHostAddress());
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
                getActivity().startService(serviceIntent);

            } else {
                // multiple files have been chosen
                for (int i = 0; i < clipdata.getItemCount(); i++) {
                    ClipData.Item item = clipdata.getItemAt(i);
                    Uri uri = item.getUri();


                    TextView statusText = (TextView) contentView.findViewById(R.id.status_text);
                    statusText.setText("Sending: " + uri);
                    Log.d(XenderActivity.TAG, "Intent----------- " + uri);
                    Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                    serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                    serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, uri.toString());
                    serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
                            info.groupOwnerAddress.getHostAddress());
                    serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
                    getActivity().startService(serviceIntent);


                }

            }
        }


    }



    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {

        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        this.info = info;
        this.getView().setVisibility(View.VISIBLE);

        // The owner IP is now known.
        TextView view = (TextView) contentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) contentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.
        if (info.groupFormed && info.isGroupOwner) {
            TextView tvStatus = contentView.findViewById(R.id.status_text);
            new FileServerAsyncTask(getActivity(), tvStatus)
                    .execute();
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case, we enable the
            // get file button.
            contentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
            ((TextView) contentView.findViewById(R.id.status_text)).setText(getResources()
                    .getString(R.string.client_text));
        }

        // hide the connect button
        contentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);

    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) contentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) contentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        contentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        TextView view = (TextView) contentView.findViewById(R.id.device_address);
        view.setText("");
        view = (TextView) contentView.findViewById(R.id.device_info);
        view.setText("");
        view = (TextView) contentView.findViewById(R.id.group_owner);
        view.setText("");
        view = (TextView) contentView.findViewById(R.id.status_text);
        view.setText("");
        contentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private File f;

        public FileServerAsyncTask(Context context, TextView statusText) {
            this.context = context;
            this.statusText = statusText;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Log.d(XenderActivity.TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(XenderActivity.TAG, "Server: connection done");
                f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");


                File dirs = new File(f.getParent());
                if (!dirs.exists()) {
                    dirs.mkdirs();
                }
                f.createNewFile();

                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();


            } catch (IOException e) {
                Log.e(XenderActivity.TAG, e.getMessage());
                return null;

            }
        }


        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

        @Override
        protected void onPostExecute(String result) {

            if (result != null) {
                statusText.setText("File Copied - " + result);
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                // intent.setDataAndType(Uri.parse("file://" + result), "image/*");

                Uri uri = FileProvider.getUriForFile(
                        context,
                        context.getApplicationContext()
                                .getPackageName() + ".provider", f);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);


                intent.setDataAndType(uri, "image/*|application/pdf|audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);


                context.startActivity(intent);

            }


        }
    }

    public static boolean copyFile(InputStream inputstream, OutputStream outputStream) {
        byte buf[] = new byte[1024];
        int len = 0;
        try {

            while ((len = inputstream.read(buf)) != -1) {// returns -1 when input stream is at the last line
                outputStream.write(buf, 0, len);
            }
            inputstream.close();
            outputStream.close();
        } catch (IOException e) {
            Log.d(XenderActivity.TAG, e.toString());

        }

        return true;
    }
}
