package com.example.androidmultimodal;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;


/**
 * Main class of the ConnectorHub library. Establishes and maintains communication between the
 * application and LearningHub. Triggers start and stop of app's recording and packages and sends
 * recording files to Hub.
 *
 * Source code adapted from ConnectorHub library (C#) by Jan Schneider
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class ConnectorHub {

    // Instructions received from the LearningHub
    public static final String areYouReady = "<ARE YOU READY?>";
    public static final String IamReady = "<I AM READY>";
    public static final String StartRecording = "<START RECORDING>";
    public static final String StopRecording = "<STOP RECORDING>";
    public static final String SendFile = "<SEND FILE>";

    DatagramSocket udpSendingSocket;
    ServerSocket myTCPListener;
    Socket tcpClientSocket;
    DatagramPacket sendPacket;

    private int TCPListenerPort;
    private int TCPSenderPort;
    private int TCPFilePort;
    private int UDPListenerPort;
    private int UDPSenderPort;
    private String HupIPAddress;
    private InetAddress serverAddr;

    long startRecordingTime;

    private RecordingObject myRecordingObject;
    public Boolean startedByHub = false;
    List<String> valuesNameDefinition;
    private RecordingApp myApp;
    public Context myAppContext;

    private Gson gson;

    private SimpleDateFormat timespanFormat;

    private Boolean IamRunning = true;

    private static String ConHub = "ConHub";

    /**
     * Interface that is implemented by the application. In this way, the app can be started from
     * the ConnectorHub after receiving the command from LearningHub.
     *
     */
    public interface RecordingApp {
        void startRecording();
        void stopRecording();
    }

    /**
     * Called from the application to link itself the the ConnectorHub instance.
     *
     * @param app Application that implements the RecordingApp interface.
     */
    public void addRecordingApp(RecordingApp app) {
        myApp = app;
    }

    protected void startRecordingEvent() {
        myApp.startRecording();
    }

    protected void stopRecordingEvent() {
        myApp.stopRecording();
    }


    public void init(int TCPSenderPort, int TCPListenerPort, int TCPFilePort,
                     int UDPSenderPort, int UDPListenerPort, String hupIPAddress) {

        Log.v(ConHub, "Initialize CH");

        this.TCPSenderPort = TCPSenderPort;
        this.TCPListenerPort = TCPListenerPort;
        this.TCPFilePort = TCPFilePort;
        this.UDPSenderPort = UDPSenderPort;
        this.UDPListenerPort = UDPListenerPort;
        this.HupIPAddress = hupIPAddress;

        createSockets();

    }

    /**
     * Creates UDP socket for sending feedback and starts thread running TCP connection
     *
     */
    private void createSockets() {
        try {
            udpSendingSocket = new DatagramSocket();
            serverAddr = InetAddress.getByName(HupIPAddress);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        Log.v(ConHub, "Start thread");
        new Thread(new tcpListenersStart()).start();

    }

    /**
     * Runnable handles communication with LearningHub via TCP. From here, recordings are called to
     * start and stop.
     *
     */
    private class tcpListenersStart implements Runnable {

        @Override
        public void run() {
            try {
                Log.v(ConHub, "Create Socket");
                Log.v(ConHub, String.valueOf(TCPListenerPort));

                myTCPListener = new ServerSocket(TCPListenerPort);
                Log.v(ConHub, "The server is running at port " + Integer.toString(TCPListenerPort) +  "...");
                Log.v(ConHub, "The local End point is  :" +
                        myTCPListener.getLocalSocketAddress());
                Log.v(ConHub, "Waiting for a connection.....");

                while (IamRunning) {
                    Socket s = myTCPListener.accept();
                    Log.v(ConHub, "Connection accepted from " + s.getRemoteSocketAddress());

                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    String receivedString = in.readLine();
                    Log.v(ConHub, "Received...");

                    if (receivedString.contains(StartRecording)) {
                        Log.v(ConHub, receivedString);
                        doStartStuff(receivedString);
                    }
                    else if (receivedString.contains(StopRecording)) {
                        Log.v(ConHub, receivedString);
                        doStopStuff();
                    }
                    // The functionality to send files from the Presentation Trainer has to be
                    // implemented on the LearningHub side
                    else if(receivedString.contains(SendFile)) {
                        //handleSendFile(receivedString);
                        Log.v(ConHub, receivedString);
                    }
                    else if (receivedString.contains(areYouReady)) {
                        sendReady();
                        Log.v(ConHub, receivedString);
                    }
                    s.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Creates RecordingObject and tells app to start recording
     *
     * @param receivedString String containing recording and application IDs
     */
    private void doStartStuff(String receivedString) {
        //"<START RECORDING>recordingID,ApplicationID</START RECORDING>"
        int startIndex = receivedString.indexOf(">") + 1;
        int startIndex2 = receivedString.indexOf(",");
        int stopIndex = receivedString.indexOf("</");

        // For creating a JSON file later
        gson = new GsonBuilder().setPrettyPrinting().create();
        myRecordingObject = new RecordingObject();
        myRecordingObject.recordingID = receivedString.substring(startIndex, startIndex2);
        myRecordingObject.applicationName = receivedString.substring(startIndex2 + 1, stopIndex);

        // SimpleDataFormat for timestamps
        timespanFormat = new SimpleDateFormat("HH:mm:ss.SSSSSSS", Locale.getDefault());
        timespanFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        startRecordingTime = System.currentTimeMillis();
        // Triggers app to start recording
        startRecordingEvent();
        startedByHub = true;
    }

    /**
     * Tells app to stop recording, terminates network operations and writes RecordingObject
     * to JSON file.
     *
     * @throws IOException
     */
    private void doStopStuff() throws IOException {
        // Triggers app to stop recording
        stopRecordingEvent();
        String json = gson.toJson(myRecordingObject);
        File path = myAppContext.getExternalFilesDir(null);
        String fileName = myRecordingObject.recordingID + myRecordingObject.applicationName + ".json";
        File file = new File(path, fileName);
        //Log.v(ConHub, file.getPath());
        FileWriter writer = new FileWriter(file);
        writer.write(json);
        Log.v(ConHub, "Wrote the file!");
        writer.close();
        sendFileTCP(file);
        // Terminate connection
        IamRunning = false;
        udpSendingSocket.close();
        myTCPListener.close();
    }

    /**
     * Function to be called from within the app if it is unexpectedly stopped i.e. by pressing back
     * or home buttons. Terminates network connections.
     *
     */
    public void unexpectedStop() {
        IamRunning = false;
        udpSendingSocket.close();
        try {
            myTCPListener.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends "I Am Ready" to LearningHub
     */
    public void sendReady() {
        new sendTCPAsync().execute(IamReady);
    }

    /**
     * Called from within the application to set names for the attributes
     *
     * @param valuesNameDefinition Attribute names used by the application
     */
    public void setValuesName(List<String> valuesNameDefinition) {
        this.valuesNameDefinition = valuesNameDefinition;
    }

    /**
     * Creates new FrameObject and adds it to the RecordingObject.
     *
     * Called from within the application.
     *
     * @param frameValues Attribute values for the frame
     */
    public void storeFrame(List<String> frameValues) {
            FrameObject f = new FrameObject(startRecordingTime, valuesNameDefinition, frameValues, timespanFormat);
            myRecordingObject.frames.add(f);
    }

    /**
     * Sends a message asynchronously via TCP
     *
     */
    private class sendTCPAsync extends AsyncTask <String, Void, Void> {

        @Override
        protected Void doInBackground(String... messages) {
            try {
                Log.v(ConHub, "Create TCP Client");
                tcpClientSocket = new Socket(HupIPAddress, TCPSenderPort);
                Log.v(ConHub, "Sucessful!");
                BufferedWriter stream = new BufferedWriter(new OutputStreamWriter(tcpClientSocket.getOutputStream()));
                stream.write(messages[0]);
                stream.flush();
                Log.v(ConHub, "Sent: " + messages[0]);
                stream.close();
                tcpClientSocket.close();
                Log.v(ConHub, "Close Client socket");
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }


    /**
     * Takes feedback and sends it to the LearningHub in JSON format
     *
     * @param feedback
     */
    public void sendFeedback(String feedback) {
        try {
            FeedbackObject f = new FeedbackObject(startRecordingTime, feedback, myRecordingObject.applicationName, timespanFormat);
            String json = gson.toJson(f);
            byte[] sendBuffer = json.getBytes("ASCII");
            sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddr, UDPSenderPort);
            udpSendingSocket.send(sendPacket);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method that gets requested file to be sent to LearningHub
     *
     * @param receivedString String containing name of the file requested
     */
    private void handleSendFile (String receivedString) {
        //"<SEND FILE>myFile.avi</SEND FILE>"
        int startIndex = receivedString.indexOf(">") + 1;
        int stopIndex = receivedString.indexOf("</");
        String fileName = receivedString.substring(startIndex, stopIndex);
        File path = myAppContext.getExternalFilesDir(null);
        File file = new File(path, fileName);
        sendFileTCP(file);

    }

    /**
     * Sends a file over TCP
     *
     * @param file
     */
    private void sendFileTCP(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            Socket socket = new Socket(serverAddr, TCPFilePort);
            OutputStream os = socket.getOutputStream();
            byte[] sendingBuffer;
            long fileLength = file.length();
            long current = 0;

            //long start = System.nanoTime();
            while(current != fileLength){
                int TCPFileBufferSize = 1024;
                if(fileLength - current >= TCPFileBufferSize)
                    current += TCPFileBufferSize;
                else{
                    TCPFileBufferSize = (int)(fileLength - current);
                    current = fileLength;
                }
                sendingBuffer = new byte[TCPFileBufferSize];
                bis.read(sendingBuffer, 0, TCPFileBufferSize);
                os.write(sendingBuffer);
                //System.out.print("Sending file ... "+(current*100)/fileLength+"% complete!");
            }

            os.flush();
            //File transfer done. Close the socket connection!
            bis.close();
            socket.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
