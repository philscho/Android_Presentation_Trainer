package com.example.androidmultimodal;

import java.util.ArrayList;
import java.util.List;

/**
 * RecordingObject that holds information about the application, the recording and a collection
 * of FrameObjects (frames of the recording)
 *
 * Source code adapted from ConnectorHub library (C#) by Jan Schneider
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class RecordingObject {

    public String recordingID;
    public String applicationName;

    public List<FrameObject> frames;

    public RecordingObject()
    {
        frames = new ArrayList<FrameObject>();
    }
}
