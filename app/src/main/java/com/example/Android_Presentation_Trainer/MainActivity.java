package com.example.Android_Presentation_Trainer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.androidmultimodal.ConnectorHub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.writer.WriterProcessor;

/**
 * Main activity of the app. Starts recording, accesses sensors and generates feedback.
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class MainActivity extends AppCompatActivity implements ConnectorHub.RecordingApp {

    public static final String KEY_TCP_SENDER_PORT = "TCP_SENDER_PORT";
    public static final String KEY_TCP_LISTENER_PORT = "TCP_LISTENER_PORT";
    public static final String KEY_TCP_FILE_PORT = "TCP_FILE_PORT";
    public static final String KEY_UDP_SENDER_PORT = "UDP_SENDER_PORT";
    public static final String KEY_UDP_LISTENER_PORT = "UDP_LISTENER_PORT";
    public static final String KEY_IP_HUB = "IP_HUB";

    static final String FEEDBACK_USE_GESTURE = "Use Gesture";
    static final String FEEDBACK_MAKE_PAUSE = "Make Pause";
    static final String FEEDBACK_SPEAK_LOUDER = "Speak louder";
    static final String FEEDBACK_SPEAK_SOFTER = "Speak softer";

    SharedPreferences sharedPref;
    Vibrator vibrator;

    TextView feedbackView;

    ConnectorHub myConnectorHub;
    List<String> attrNames;
    List<String> attributeValues;
    SimpleDateFormat timeSpanFormat;
    long startRecordingTime;

    private boolean mShouldContinue; // Indicates if recording / playback should stop

    private final int AUDIO_SAMPLE_RATE = 16000;    //1600kHz
    private final int AUDIO_BUFFER_SIZE = 1024;     //1024 bytes
    private AudioDispatcher dispatcher;

    double silenceThreshold = -100; //less than is silent
    double quietThreshold = -75;    //less than is quiet
    double loudThreshold = -55;     //more than is loud

    private final long moveInterval = 6000L;

    boolean isPausing = false;

    boolean louderInstruction;
    boolean quieterInstruction;
    boolean pauseInstruction = false;
    boolean moveInstruction = false;

    boolean newAudioData, newSensorData;
    private Lock lock;
    private Condition newData;

    BlockingQueue<String> feedbackList;

    Thread audioThread;
    Thread frameThread;
    handleAccelerometer accelerometerThread;

    /**
     * Prepares for recording and establishes connection to LearningHub
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_feedback);
        //Log.i("DFA", "ON CREATE");

        // Keeps the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        feedbackView = findViewById(R.id.textView_feedback);
        feedbackView.setText("Waiting...");

        // Initialize attribute arrays for sensor data
        attrNames = new ArrayList<String>();
        attributeValues = new ArrayList<>(4);
        attrNames.add(0, "VOLUME");
        attrNames.add(1, "X COORDINATE");
        attrNames.add(2, "Y COORDINATE");
        attrNames.add(3, "Z COORDINATE");
        attributeValues.add("");
        attributeValues.add("");
        attributeValues.add("");
        attributeValues.add("");

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Gets ports and IP address from shared preferences
        int tcpSenderPort = sharedPref.getInt(KEY_TCP_SENDER_PORT, 00000);
        int tcpListenerPort = sharedPref.getInt(KEY_TCP_LISTENER_PORT, 00000);
        int udpSenderPort = sharedPref.getInt(KEY_UDP_SENDER_PORT, 00000);
        int udpListenerPort = sharedPref.getInt(KEY_UDP_LISTENER_PORT, 00000);
        int tcpFilePort = sharedPref.getInt(KEY_TCP_FILE_PORT, 00000);
        String ipHub = sharedPref.getString(KEY_IP_HUB, "00000");

        myConnectorHub = new ConnectorHub();
        // Necessary for accessing file storage to send files
        myConnectorHub.myAppContext = getApplicationContext();
        // Initializes connection to LearningHub
        myConnectorHub.init(tcpSenderPort, tcpListenerPort, tcpFilePort, udpSenderPort,
                udpListenerPort, ipHub);
        // Applications links itself to ConnectorHub instance to be started from there
        myConnectorHub.addRecordingApp(this);
        // Set attribute names in ConnectorHub
        myConnectorHub.setValuesName(attrNames);

    }

    @Override
    protected void onPause() {
        super.onPause();
        //Log.i("DFA", "ON PAUSE");
    }

    /**
     * Stops recording and terminates connection in case of accidental termination (i.e. pressing
     * home or back button).
     *
     */
    @Override
    protected void onStop() {
        super.onStop();
        //Log.i("DFA", "ON STOP");

        // Stops frame thread and feedback thread
        mShouldContinue = false;

        // Stops accelerometer thread
        if (accelerometerThread != null)
            accelerometerThread.stopThread();

        // Stops audio thread
        if (dispatcher != null && !dispatcher.isStopped())
            dispatcher.stop();

        // Stops network threads and terminates connections
        myConnectorHub.unexpectedStop();

        // Kill this activity
        this.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.i("DFA", "ON DESTROY");
    }


    /**
     * Starts recording.
     *
     * This method is called from the ConnectorHub class.
     *
     */
    public void startRecording() {

        startRecordingTime = System.currentTimeMillis();

        // Check for write access to ext. storage
        String state = Environment.getExternalStorageState();
        if (! Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e("Storage", "Can't access file storage. Can't write files!");
            return;
        }

        mShouldContinue = true;

        // For formatting timestamps
        timeSpanFormat = new SimpleDateFormat("HH:mm:ss.SSSSSSS", Locale.getDefault());
        timeSpanFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Lock and condition for synchronized access to attributeValues array
        lock = new ReentrantLock();
        newData = lock.newCondition();

        //Start frame thread
        frameThread = new generateFrames();
        frameThread.start();

        //Start feedback thread
        feedbackList = new LinkedBlockingQueue<>();
        handleFeedback feedbackThread = new handleFeedback();
        feedbackThread.start();

        //Start accelerometer thread
        accelerometerThread = new handleAccelerometer();
        accelerometerThread.run();

        //Start audio thread
        dispatcher = new handleAudio().getAudioDispatcher();
        audioThread = new Thread(dispatcher);
        audioThread.start();

        feedbackView.post(new Runnable() {
            @Override
            public void run() {
                feedbackView.setText("Start!");
            }
        });
    }

    /**
     * Stops the recording and goes back to the launch activity.
     *
     * This method is called from the ConnectorHub class.
     *
     */
    public void stopRecording() {

        // Stops frame thread and feedback thread
        mShouldContinue = false;

        // Stops accelerometer thread
        accelerometerThread.stopThread();

        // Stops audio thread
        if (!dispatcher.isStopped())
            dispatcher.stop();

        // Go back to entry activity
        Intent intent = new Intent(this, LaunchActivity.class);
        startActivity(intent);
        // Kill this activity
        this.finish();

        // Necessary for accessing file storage to send files
        myConnectorHub.myAppContext = getApplicationContext();
    }

    /**
     * Class that handles the creation of frames.
     *
     */
    class generateFrames extends Thread {

        @Override
        public void run() {
            while(mShouldContinue) {
                lock.lock();
                try {
                    while (!(newAudioData && newSensorData))
                        // Waits until producer threads signal that new data is available in
                        // attributeValues.
                        newData.await();
                    myConnectorHub.storeFrame(attributeValues);
                    //Log.v("FRAME", "Created new frame");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    newAudioData = false;
                    newSensorData = false;
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Class that handles the sending and displaying of feedback.
     *
     */
    class handleFeedback extends Thread {

        String feedback;
        Boolean sent = false;

        @Override
        public void run() {

            // Begin to give feedback 10 seconds after start
            try { Thread.sleep(10000); }
            catch (InterruptedException e) { e.printStackTrace(); }

            while(mShouldContinue) {
                // Wait 6 seconds between feedback
                if (sent) {
                    try {
                        Thread.sleep(6000);
                        sent = false;
                    }
                    catch (InterruptedException e) { e.printStackTrace(); }
                }
                try {
                    // Will wait if queue is empty
                    feedback = feedbackList.take();

                    switch(feedback) {
                        case FEEDBACK_USE_GESTURE:
                            if (moveInstruction) {
                                myConnectorHub.sendFeedback(feedback);
                                // Display feedback on screen
                                feedbackView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        feedbackView.setText(FEEDBACK_USE_GESTURE);
                                    }
                                });
                                // Let device vibrate
                                vibrator.vibrate(200);
                                sent = true;
                                // The specific feedback has been given
                                moveInstruction = false;
                            }
                        case FEEDBACK_SPEAK_SOFTER:
                            if (quieterInstruction) {
                                myConnectorHub.sendFeedback(feedback);
                                feedbackView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        feedbackView.setText(FEEDBACK_SPEAK_SOFTER);
                                    }
                                });
                                vibrator.vibrate(200);
                                sent = true;
                                quieterInstruction = false;
                            }
                        case FEEDBACK_SPEAK_LOUDER:
                            if (louderInstruction) {
                                myConnectorHub.sendFeedback(feedback);
                                feedbackView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        feedbackView.setText(FEEDBACK_SPEAK_LOUDER);
                                    }
                                });
                                vibrator.vibrate(200);
                                sent = true;
                                louderInstruction = false;
                            }
                        case FEEDBACK_MAKE_PAUSE:
                            if (pauseInstruction) {
                                myConnectorHub.sendFeedback(feedback);
                                feedbackView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        feedbackView.setText(FEEDBACK_MAKE_PAUSE);
                                    }
                                });
                                vibrator.vibrate(200);
                                sent = true;
                                pauseInstruction = false;
                            }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Class that handles the linear accelerometer.
     *
     * Implements SensorEventListener for receiving sensor data. Provides method for gesture
     * detection.
     *
     */
    class handleAccelerometer implements Runnable, SensorEventListener {

        private SensorManager sensorManager;
        private Sensor linAccelerometer;
        private HandlerThread handlerThread;
        private Handler handler;

        private long lastGesture = 0;

        @Override
        public void run() {
            // Creates new thread on which the sensor data is worked with
            handlerThread = new HandlerThread("Accelerometer Thread",
                    Process.THREAD_PRIORITY_MORE_FAVORABLE);
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            // Get linear accelerometer
            linAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            sensorManager.registerListener(this, linAccelerometer,
                    SensorManager.SENSOR_DELAY_UI, handler);
            lastGesture = System.currentTimeMillis();
        }

        /**
         * Sensor data is received in this function. Inserts data in value array and calls
         * gesture detection method.
         *
         * @param event Event created by the sensor. Primarily holds the data.
         *
         */
        @Override
        public void onSensorChanged(SensorEvent event) {
            Sensor mySensor = event.sensor;

            if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                long now = System.currentTimeMillis();
                String xV = Float.toString(x);
                String yV = Float.toString(y);
                String zV = Float.toString(z);
                //String timeStr = timeSpanFormat.format(new Date(now - startRecordingTime));
                //Log.v("ACC", timeStr + " -- " + xV + ";" + yV + ";" + zV);

                detectGesture(x, y, z, now);

                lock.lock();
                try {
                    attributeValues.set(1, xV);
                    attributeValues.set(2, yV);
                    attributeValues.set(3, zV);
                    // Signal that new data has been set
                    newSensorData = true;
                    newData.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        /**
         * Detects whether a gesture was made. Creates feedback after 6 seconds without gesture.
         *
         *
         * @param x
         * @param y
         * @param z
         * @param time
         *
         */
        private void detectGesture(float x, float y, float z, long time) {
            // No gesture detection when the user is not speaking
            if (isPausing) {
                lastGesture = time;
                return;
            }
            long diff = time - lastGesture;
            //String sTime = String.valueOf(time);
            if ((x > Math.abs(5)) || (y > Math.abs(5)) || (z > Math.abs(5))) {
                //Log.v(GES, sTime);
                lastGesture = time;
                moveInstruction = false;
            }
            if (((x < Math.abs(5)) || (y < Math.abs(5)) || (z < Math.abs(5))) &&
                    (diff > moveInterval) && !moveInstruction) {
                feedbackList.add(FEEDBACK_USE_GESTURE);
                moveInstruction = true;
            }
        }


        public void stopThread() {

            //Unregister the listener
            if(sensorManager != null) {
                sensorManager.unregisterListener(this);
            }

            if(handlerThread.isAlive())
                handlerThread.quitSafely();
        }
    }

    /**
     * Class for accessing audio data with method for volume detection.
     *
     */
    class handleAudio {

        int pauseTime = 0;
        int speakingTime = 0;
        int noOfQuiet = 0;
        int noOfNormal = 0;
        int noOfLoud = 0;
        int length;
        Queue<Double> volumeRange = new LinkedList<>();

        /**
         * Creates a TarsosDSP AudioDispatcher that fetches audio data from the microphone,
         * calculates dBSPL value (volume) and saves data to a wav file.
         *
         * @return AudioDispatcher object. Functions as Runnable.
         *
         */
        public AudioDispatcher getAudioDispatcher() {

            // Audio buffer holds 0.064 seconds / 64 milliseconds
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(AUDIO_SAMPLE_RATE,
                    AUDIO_BUFFER_SIZE, 0);
            dispatcher.addAudioProcessor(new AudioProcessor() {

                // AudioProcessor for calculating dBSPL value, calling volume detection method and
                // setting data to values array.
                @Override
                public boolean process(AudioEvent audioEvent) {
                    double volume = audioEvent.getdBSPL();
                    String value = Double.toString(volume);

                    //long now = System.currentTimeMillis();
                    //String timeStr = timeSpanFormat.format(new Date(now - startRecordingTime));
                    //double time = Double.parseDouble(timeStr);
                    //Log.v("AUDIO", timeStr + " -- " + value);

                    volumeDetection(volume);
                    lock.lock();
                    try {
                        attributeValues.set(0, value);
                        newAudioData = true;
                        newData.signal();
                    }
                    finally {
                        lock.unlock();
                    }
                    return true;
                }

                @Override
                public void processingFinished() { }
            });

            // Add audio processor for saving audio stream to WAV-file
            try {
                String state = Environment.getExternalStorageState();
                if ( !Environment.MEDIA_MOUNTED.equals(state) ) {
                    Log.e("Storage", "Can't access file storage. Can't write files!");
                    return dispatcher;
                }
                // Saves file to publicly accessible folder
                File path = getApplicationContext().getExternalFilesDir(null);
                String fileName = "audioFile.wav";
                File file = new File(path, fileName);
                RandomAccessFile wavFile = new RandomAccessFile(file, "rw");
                wavFile.setLength(0);

                dispatcher.addAudioProcessor(new WriterProcessor(dispatcher.getFormat(), wavFile));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return dispatcher;
        }

        /**
         * Method for detection of voice volume from dBSPL value. Parameters are set in outer class.
         * Generates feedback for making a pause and speaking softer or louder.
         *
         * @param volume dBSPL value
         */
        private void volumeDetection(double volume) {
            volumeRange.add(volume);
            length = volumeRange.size();
            speakingTime++;

            //Log.v("Volume", String.valueOf(volume));
            //Log.v("Length", String.valueOf(length));

            if (volume < silenceThreshold) {
                pauseTime++;
                //Log.v("SILENCE", String.valueOf(volume));

                // 5 buffers hold ~ 0.2 - 0.3 seconds
                if (pauseTime >= 5) {
                    // Speaking time is reset when a pause is detected
                    speakingTime = 0;
                    isPausing = true;

                    //Log.v("VF", "PAUSE detected");
                }
            }
            else {
                // Pause time has to be continuous
                pauseTime = 0;
                isPausing = false;
                //Log.v("Speaking Time", String.valueOf(speakingTime));

                if (volume < quietThreshold) {noOfQuiet++;
                    //Log.v("QUIET", String.valueOf(volume));
                }
                else if (volume < loudThreshold) {noOfNormal++;
                    //Log.v("NORMAL", String.valueOf(volume));
                }
                else if (volume >= loudThreshold) {noOfLoud++;
                    //Log.v("LOUD", String.valueOf(volume));
                }
            }
            // 235 buffers hold ~15 seconds.
            if (speakingTime >= 235 && !pauseInstruction) {
                pauseInstruction = true;
                //Log.v("VF", "Feedback: Make pause");
                feedbackList.add(FEEDBACK_MAKE_PAUSE);
            }

            // 10 buffers hold ~0.64 seconds
            if (length >= 10) {
                if (length == 11) {
                    double old = volumeRange.remove();
                    if (old < silenceThreshold) {
                        //Log.v("Old", String.valueOf(old));
                    }
                    else if (old < quietThreshold) {
                        noOfQuiet--;
                        //Log.v("Old", String.valueOf(old));
                    }
                    else if (old < loudThreshold) {
                        noOfNormal--;
                        //Log.v("Old", String.valueOf(old));
                    }
                    else if (old >= loudThreshold) {
                        noOfLoud--;
                        //Log.v("Old", String.valueOf(old));
                    }
                }
                // Generates feedback when volume is detected in a certain range for 0.64 seconds
                if (noOfQuiet == 10 && !louderInstruction) {
                    //Log.v("VF", "Feedback: Speak louder");

                    // Cancels other instructions so that only one feedback for volume is true at
                    // a time
                    louderInstruction = true;
                    quieterInstruction = false;
                    // Resets volume data once feedback was generated
                    clearVolumeRange();
                    feedbackList.add(FEEDBACK_SPEAK_LOUDER);
                }
                else if (noOfNormal == 10) {
                    louderInstruction = false;
                    quieterInstruction = false;
                }
                else if (noOfLoud == 10 && !quieterInstruction) {
                    //Log.v("VF", "Feedback: Speak quieter");
                    quieterInstruction = true;
                    louderInstruction = false;
                    clearVolumeRange();
                    feedbackList.add(FEEDBACK_SPEAK_SOFTER);
                }
            }
        }

        /**
         * Resets all collected volume data so that data is not reused.
         *
         */
        private void clearVolumeRange() {
            volumeRange.clear();
            noOfQuiet = 0;
            noOfNormal = 0;
            noOfLoud = 0;
            speakingTime = 0;
            pauseTime = 0;
            pauseInstruction = false;
            isPausing = false;
        }
    }

}

